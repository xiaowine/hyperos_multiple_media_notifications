package cc.xiaowine.nmm

import android.app.Application
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArraySet
import kotlin.concurrent.Volatile

/**
 * 模块宿主 Application。
 *
 * <p>只服务于模块自身的设置界面：连接 LSPosed 服务，并把服务绑定/断开状态
 * 分发给当前打开的界面。</p>
 */
class App : Application(), XposedServiceHelper.OnServiceListener {

    companion object {
        /** 当前已连接的 LSPosed 服务；未连接时为 null。 */
        @Volatile
        private var currentService: XposedService? = null

        /** 只读暴露当前服务，避免界面意外改写连接状态。 */
        val service: XposedService?
            get() = currentService

        /** 界面侧服务状态监听器集合；并发集合避免回调期间增删导致异常。 */
        private val serviceStateListeners = CopyOnWriteArraySet<ServiceStateListener>()

        /** 向单个监听器分发状态；监听器已经移除时不再回调。 */
        private fun dispatchServiceState(
            listener: ServiceStateListener,
            current: XposedService?
        ) {
            if (serviceStateListeners.contains(listener)) {
                listener.onServiceStateChanged(current)
            }
        }

        /** 注册服务状态监听器；notifyImmediately 为 true 时立即回调当前状态。 */
        fun addServiceStateListener(
            listener: ServiceStateListener,
            notifyImmediately: Boolean
        ) {
            serviceStateListeners.add(listener)
            if (notifyImmediately) {
                dispatchServiceState(listener, currentService)
            }
        }

        /** 移除服务状态监听器。 */
        fun removeServiceStateListener(listener: ServiceStateListener) {
            serviceStateListeners.remove(listener)
        }
    }

    /** 把最新服务状态广播给所有监听器。 */
    private fun notifyServiceStateChanged(current: XposedService?) {
        for (listener in serviceStateListeners) {
            dispatchServiceState(listener, current)
        }
    }

    /** Application 创建时注册 LSPosed 服务监听。 */
    override fun onCreate() {
        super.onCreate()
        XposedServiceHelper.registerListener(this)
    }

    /** 界面侧监听 LSPosed 服务状态的回调。 */
    interface ServiceStateListener {
        /** 服务绑定成功或断开时回调；service 为 null 表示已断开。 */
        fun onServiceStateChanged(service: XposedService?)
    }

    /** LSPosed 服务绑定成功。 */
    override fun onServiceBind(service: XposedService) {
        currentService = service
        notifyServiceStateChanged(service)
    }

    /** LSPosed 服务断开。 */
    override fun onServiceDied(service: XposedService) {
        currentService = null
        notifyServiceStateChanged(null)
    }
}
