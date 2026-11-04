package cc.xiaowine.nmm

import android.util.Log
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface.HotReloadedParam
import io.github.libxposed.api.XposedModuleInterface.HotReloadingParam
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam
import io.github.libxposed.api.XposedModuleInterface.PackageReadyParam
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.hook.HookInstaller
import cc.xiaowine.nmm.pager.ui.PagerIndicator

/**
 * LSPosed Modern API 模块入口。
 *
 * <p>负责在 SystemUI 进程中安装 hook，并处理热加载：旧 generation 保存
 * ClassLoader、controller 和选中页，新 generation 恢复后重新安装 hook、清理旧指示器。</p>
 */
class MediaPagerModule : XposedModule() {
    /** 当前 generation 是否已成功安装 hook。 */
    private var installed = false

    /** 模块加载时记录进程名，便于确认作用域是否生效。 */
    override fun onModuleLoaded(param: ModuleLoadedParam) {
        log(Log.INFO, Constants.TAG, "loaded in ${param.processName}")
    }

    /** SystemUI 包就绪时安装 hook；同一 generation 只安装一次。 */
    override fun onPackageReady(param: PackageReadyParam) {
        if (param.packageName != Constants.PACKAGE) return

        synchronized(this) {
            if (installed) return
            log(Log.INFO, Constants.TAG, "installing for ${param.packageName}")
            try {
                if (HookInstaller.install(this, param.classLoader)) {
                    installed = true
                }
            } catch (t: Throwable) {
                log(Log.ERROR, Constants.TAG, "install failed", t)
            }
        }
    }

    /**
     * 热加载前由旧 generation 调用。
     *
     * <p>热加载不会重放 onPackageReady，而且新 generation 的静态状态为空，
     * 所以把 SystemUI ClassLoader、controller、selectedKey 通过 saved state 传过去。</p>
     */
    override fun onHotReloading(param: HotReloadingParam): Boolean {
        if (!installed) return false
        val classLoader = PagerRuntime.classLoader ?: return false

        val state = HashMap<String, Any?>()
        state["classLoader"] = classLoader
        state["controller"] = PagerRuntime.rawController
        state["selectedKey"] = PagerRuntime.selectedKey
        param.setSavedInstanceState(state)
        log(Log.INFO, Constants.TAG, "hot reload requested")
        return true
    }

    /**
     * 新 generation 接管。
     *
     * <p>先安装新 hook，成功后再卸载旧 hook，避免安装失败时模块完全失效；
     * 最后在主线程清理旧圆点并刷新，防止重复叠加。</p>
     */
    override fun onHotReloaded(param: HotReloadedParam) {
        try {
            val state = param.savedInstanceState as? Map<*, *>
            val classLoader = state?.get("classLoader") as? ClassLoader
            if (classLoader == null) {
                log(Log.WARN, Constants.TAG, "hot reload: no classloader, restart required")
                return
            }

            PagerRuntime.updateController(state["controller"])
            val controller = PagerRuntime.controller
            PagerRuntime.selectedKey = state["selectedKey"] as? String

            if (!HookInstaller.install(this, classLoader)) {
                log(Log.ERROR, Constants.TAG, "hot reload install failed; old hooks kept")
                return
            }

            installed = true
            for (handle in param.oldHookHandles) {
                try {
                    handle.unhook()
                } catch (t: Throwable) {
                    log(Log.WARN, Constants.TAG, "old hook unhook failed", t)
                }
            }

            // onHotReloaded 运行在 binder 线程，View 清理和刷新必须切到主线程。
            PagerRuntime.onMain {
                PagerIndicator.cleanup(controller)
                PagerIndicator.refresh(controller)
            }
            log(Log.INFO, Constants.TAG, "hot reload installed")
        } catch (t: Throwable) {
            log(Log.ERROR, Constants.TAG, "hot reload failed", t)
        }
    }
}
