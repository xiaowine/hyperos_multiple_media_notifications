package cc.xiaowine.nmm.pager.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.MediaCard
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SystemUiClasses
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * SystemUI 类解析与 hook 安装入口。
 *
 * <p>必需类型（controller/view/header/MediaData）缺失时整体放弃；
 * 可选类型（排序工具、SwipeHelper 等）缺失时只关闭对应能力。
 * 热加载复用同一套安装流程，但保留已有的 controller 和 selectedKey。</p>
 */
internal object HookInstaller {
    /** 解析类型并安装全部 hook；必需类型缺失时返回 false。 */
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        PagerRuntime.classLoader = classLoader

        val classes = resolve(module, classLoader) ?: return false
        PagerRuntime.classes = classes
        MediaCard.install(classes)

        ControllerHooks.install(module, classes)
        ViewHooks.install(module, classes)

        val gestures = SwipeHooks.install(module, classes)
        val dismiss = DismissHooks.install(module, classLoader)
        val hasSetTop = Reflect.method(
            classes.controller,
            "access\$setTopMediaData",
            classes.controller,
            classes.mediaData
        ) != null

        module.log(
            Log.INFO,
            Constants.TAG,
            "hooks installed: gestures=$gestures dismiss=$dismiss setTop=$hasSetTop"
        )
        return true
    }

    /** 解析必需和可选的 SystemUI 类型。 */
    private fun resolve(module: XposedModule, classLoader: ClassLoader): SystemUiClasses? {
        val controller = loadRequired(module, classLoader, Constants.CONTROLLER, "controller")
            ?: return null
        val mediaView = loadRequired(module, classLoader, Constants.MEDIA_VIEW, "media view")
            ?: return null
        val header = loadRequired(module, classLoader, Constants.HEADER, "header")
            ?: return null
        val mediaData = loadRequired(module, classLoader, Constants.MEDIA_DATA, "MediaData")
            ?: return null

        return SystemUiClasses(
            controller = controller,
            mediaView = mediaView,
            header = header,
            mediaData = mediaData,
            sortUtils = Reflect.loadClass(Constants.SORT_UTILS, classLoader),
            swipeHelper = Reflect.loadClass(Constants.SWIPE_HELPER, classLoader),
            notificationSwipeHelper =
                Reflect.loadClass(Constants.NOTIFICATION_SWIPE_HELPER, classLoader),
            miuiSwipeHelper = Reflect.loadClass(Constants.MIUI_SWIPE_HELPER, classLoader),
            menuRowPlugin = Reflect.loadClass(Constants.MENU_ROW_PLUGIN, classLoader)
        )
    }

    /** 加载必需类型；失败时记录错误并返回 null。 */
    private fun loadRequired(
        module: XposedModule,
        classLoader: ClassLoader,
        name: String,
        label: String
    ): Class<*>? {
        val type = Reflect.loadClass(name, classLoader)
        if (type == null) {
            module.log(Log.ERROR, Constants.TAG, "$label class missing: $name")
        }
        return type
    }
}
