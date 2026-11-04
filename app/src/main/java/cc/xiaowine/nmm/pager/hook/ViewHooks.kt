package cc.xiaowine.nmm.pager.hook

import io.github.libxposed.api.XposedModule
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SystemUiClasses
import cc.xiaowine.nmm.pager.ui.PagerIndicator
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * 媒体卡 View 侧 hook。
 *
 * <p>媒体卡 attach 时从 header 的 mediaNotificationController 字段捕获 controller，
 * 并刷新圆点。这个捕获路径比构造器更接近 UI 生命周期，能在热加载/重建后恢复。</p>
 */
internal object ViewHooks {
    /** 安装 View 侧 hook。 */
    fun install(module: XposedModule, classes: SystemUiClasses) {
        hookHeader(module, classes)
    }

    /** 媒体卡 onAttachedToWindow 后捕获 controller 并触发圆点刷新。 */
    private fun hookHeader(module: XposedModule, classes: SystemUiClasses) {
        val method = Reflect.method(classes.header, "onAttachedToWindow") ?: run {
            module.log(android.util.Log.WARN, Constants.TAG, "header attach method missing")
            return
        }

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val header = chain.thisObject
            val linked = Reflect.getField(header, "mediaNotificationController")
            if (linked != null) {
                PagerRuntime.updateController(linked)
            }
            PagerIndicator.refreshLater(PagerRuntime.controller)
            result
        }
    }
}
