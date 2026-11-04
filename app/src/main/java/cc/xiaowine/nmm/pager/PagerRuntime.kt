package cc.xiaowine.nmm.pager

import android.os.Handler
import android.os.Looper

/**
 * SystemUI 进程内的运行时状态。
 *
 * <p>通知栏只有一个媒体卡实例，因此 controller 和当前选中页使用进程级单例保存。
 * controller 以 [ControllerHandle] 形式暴露，避免和 MediaData 等其他反射对象混用。</p>
 */
internal object PagerRuntime {
    /** SystemUI 的 ClassLoader，热加载恢复时使用。 */
    @Volatile
    var classLoader: ClassLoader? = null

    /** 当前安装解析出的类型集合；未安装时为 null。 */
    @Volatile
    var classes: SystemUiClasses? = null

    /** 当前媒体卡 NodeController 句柄。 */
    @Volatile
    var controller: ControllerHandle? = null

    /** 用户手动选中的媒体页 key；null 表示跟随系统 topMediaData。 */
    @Volatile
    var selectedKey: String? = null

    /** 主线程 Handler，懒创建。 */
    @Volatile
    private var mainHandler: Handler? = null

    /** 保存 controller 原始对象；热加载 state 只接受原始引用。 */
    val rawController: Any?
        get() = controller?.raw

    /** 从 hook 边界的原始对象更新 controller。 */
    fun updateController(raw: Any?) {
        controller = raw?.let(::ControllerHandle)
    }

    /** 如果当前不在主线程，把任务投递到主线程；否则直接执行。 */
    fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
            return
        }
        val handler = synchronized(this) {
            mainHandler ?: Handler(Looper.getMainLooper()).also { mainHandler = it }
        }
        handler.post(block)
    }
}
