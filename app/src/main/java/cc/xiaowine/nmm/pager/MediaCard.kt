package cc.xiaowine.nmm.pager

import android.util.Log
import android.view.View
import android.view.ViewGroup
import cc.xiaowine.nmm.pager.util.Reflect
import java.lang.reflect.Method

/**
 * 媒体卡的反射访问层。
 *
 * <p>对外只接受 [ControllerHandle] 和 [MediaDataHandle]，把裸 Any 限制在
 * 本对象的反射调用内部。构造时解析一次方法，运行期只做 invoke。</p>
 */
internal object MediaCard {
    /** 控制器静态合成方法 access$setTopMediaData；不可用时为 null。 */
    private var setTopMethod: Method? = null

    /** 媒体视图绑定方法 bindMediaData；fallback 时使用。 */
    private var bindMethod: Method? = null

    /** 解析与媒体卡相关的方法；安装和热加载时各调用一次。 */
    fun install(classes: SystemUiClasses) {
        setTopMethod = Reflect.method(
            classes.controller,
            "access\$setTopMediaData",
            classes.controller,
            classes.mediaData
        )
        bindMethod = Reflect.method(classes.mediaView, "bindMediaData", classes.mediaData)
    }

    /** 返回媒体卡根 View（MiuiMediaHeaderView）。 */
    fun card(controller: ControllerHandle?): View? =
        Reflect.getField(controller?.raw, "mediaContainerView") as? View

    /** 返回媒体卡内部的 player（根 ConstraintLayout）。 */
    fun player(controller: ControllerHandle?): ViewGroup? {
        val holder = Reflect.getField(controller?.raw, "mediaViewHolder")
        return Reflect.getField(holder, "player") as? ViewGroup
    }

    /** 返回系统当前 topMediaData。 */
    fun topData(controller: ControllerHandle?): MediaDataHandle? {
        val data = Reflect.getField(controller?.raw, "topMediaData") ?: return null
        return MediaDataHandle(data)
    }

    /**
     * 让控制器切换到指定 MediaData。
     *
     * <p>优先调用原厂静态合成方法，它会同步 topMediaData、监听器和副屏状态。
     * 方法缺失时才退回“直接写字段 + bindMediaData + 通知监听器”。</p>
     *
     * @return 是否成功切换
     */
    fun setTop(controller: ControllerHandle, data: MediaDataHandle): Boolean {
        val method = setTopMethod
        if (method != null) {
            try {
                method.invoke(null, controller.raw, data.raw)
                return true
            } catch (t: Throwable) {
                Log.e(Constants.TAG, "setTopMediaData failed, fallback", t)
            }
        }
        return setTopFallback(controller, data)
    }

    /** setTopMediaData 不可用时的降级路径。 */
    private fun setTopFallback(controller: ControllerHandle, data: MediaDataHandle): Boolean {
        val rawController = controller.raw
        if (!Reflect.setField(rawController, "topMediaData", data.raw)) {
            Log.e(Constants.TAG, "topMediaData field missing")
            return false
        }

        val viewController = Reflect.getField(rawController, "mediaViewController")
        val bind = bindMethod
        if (viewController != null && bind != null) {
            try {
                bind.invoke(viewController, data.raw)
            } catch (t: Throwable) {
                Log.e(Constants.TAG, "bindMediaData failed", t)
            }
        }

        notifyTopListeners(controller, data)
        return true
    }

    /** 手动通知 topMediaListeners，保持侧滑菜单等依赖方拿到新 MediaData。 */
    private fun notifyTopListeners(controller: ControllerHandle, data: MediaDataHandle) {
        val mediaDataClass = PagerRuntime.classes?.mediaData ?: return
        val listeners = Reflect.getField(controller.raw, "topMediaListeners") as? Iterable<*>
            ?: return
        for (listener in listeners) {
            if (listener == null) continue
            val method = Reflect.findMethod(
                listener.javaClass,
                "onMediaDataChanged",
                mediaDataClass
            ) ?: continue
            try {
                method.invoke(listener, data.raw)
            } catch (t: Throwable) {
                Log.w(Constants.TAG, "notify top listener failed", t)
            }
        }
    }
}
