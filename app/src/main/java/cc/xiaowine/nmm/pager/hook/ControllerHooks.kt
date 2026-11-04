package cc.xiaowine.nmm.pager.hook

import android.util.Log
import io.github.libxposed.api.XposedModule
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.ControllerHandle
import cc.xiaowine.nmm.pager.MediaCard
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SortUtilsHandle
import cc.xiaowine.nmm.pager.SystemUiClasses
import cc.xiaowine.nmm.pager.data.MediaPageStore
import cc.xiaowine.nmm.pager.ui.PagerIndicator
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * 媒体卡 NodeController / ViewController 侧 hook。
 *
 * <p>负责捕获 controller、在多会话时替换 setTopMediaData 的参数，并在系统完成
 * 重新绑定后刷新圆点。所有 hook 只做参数替换或结果转发，不重写原厂绑定逻辑。</p>
 */
internal object ControllerHooks {
    /** 安装控制器侧全部 hook。 */
    fun install(module: XposedModule, classes: SystemUiClasses) {
        hookConstructors(module, classes.controller)
        hookInstanceCapture(module, classes.controller)
        hookSetTop(module, classes)
        hookGetTop(module, classes)
        hookBind(module, classes)
    }

    /** 拦截所有构造器，构造完成后捕获 controller 实例。 */
    private fun hookConstructors(module: XposedModule, controllerClass: Class<*>) {
        for (constructor in controllerClass.declaredConstructors) {
            module.hook(constructor).intercept { chain ->
                val result = chain.proceed()
                PagerRuntime.updateController(chain.thisObject)
                result
            }
        }
    }

    /** 构造器回调里 thisObject 不一定可靠，再用构造后立即调用的两个实例方法兜底捕获。 */
    private fun hookInstanceCapture(module: XposedModule, controllerClass: Class<*>) {
        Reflect.method(controllerClass, "loadLayout\$1")?.let { method ->
            module.hook(method).intercept { chain ->
                PagerRuntime.updateController(chain.thisObject)
                chain.proceed()
            }
        }
        Reflect.method(controllerClass, "getNodeLabel")?.let { method ->
            module.hook(method).intercept { chain ->
                PagerRuntime.updateController(chain.thisObject)
                chain.proceed()
            }
        }
    }

    /**
     * 拦截 access$setTopMediaData(controller, data)。
     *
     * <p>如果用户手动选过页，就把参数替换成 selectedKey 对应的最新 MediaData；
     * 选中页已从会话列表消失时清空 selectedKey，回退系统原本的 top。</p>
     */
    private fun hookSetTop(module: XposedModule, classes: SystemUiClasses) {
        val method = Reflect.method(
            classes.controller,
            "access\$setTopMediaData",
            classes.controller,
            classes.mediaData
        ) ?: run {
            module.log(
                Log.WARN,
                Constants.TAG,
                "access\$setTopMediaData missing; getTopMediaData fallback only"
            )
            return
        }

        module.hook(method).intercept { chain ->
            val args = chain.args.toTypedArray()
            val rawController = args.getOrNull(0)
            if (args.size >= 2 && rawController != null) {
                val controller = ControllerHandle(rawController)
                val selected = PagerRuntime.selectedKey
                if (selected != null) {
                    val page = MediaPageStore.findPage(controller, selected)
                    if (page != null) {
                        args[1] = page.raw
                    } else {
                        PagerRuntime.selectedKey = null
                    }
                }
            }

            val result = chain.proceed(args)
            rawController?.let {
                PagerIndicator.refreshLater(ControllerHandle(it))
            }
            result
        }
    }

    /** access$setTopMediaData 缺失时，拦截 getTopMediaData 直接返回选中页。 */
    private fun hookGetTop(module: XposedModule, classes: SystemUiClasses) {
        val sortUtilsClass = classes.sortUtils ?: return
        val method = Reflect.method(sortUtilsClass, "getTopMediaData") ?: return

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val selected = PagerRuntime.selectedKey
            val rawSortUtils = chain.thisObject
            if (selected == null || rawSortUtils == null) {
                result
            } else {
                val page = MediaPageStore.findPageInSort(
                    SortUtilsHandle(rawSortUtils),
                    selected
                )
                if (page != null) {
                    page.raw
                } else {
                    PagerRuntime.selectedKey = null
                    result
                }
            }
        }
    }

    /** 系统完成 bindMediaData 后刷新圆点。 */
    private fun hookBind(module: XposedModule, classes: SystemUiClasses) {
        val method = Reflect.method(classes.mediaView, "bindMediaData", classes.mediaData)
            ?: return

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            PagerIndicator.refreshLater(PagerRuntime.controller)
            result
        }
    }
}
