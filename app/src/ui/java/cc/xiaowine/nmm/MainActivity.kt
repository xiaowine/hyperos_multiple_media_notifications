package cc.xiaowine.nmm

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import cc.xiaowine.nmm.databinding.ActivityMainBinding
import cc.xiaowine.nmm.pager.Constants
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedService.OnScopeEventListener

/**
 * 模块设置与调试界面。
 *
 * <p>显示 LSPosed 服务、API、作用域和运行目标，并提供申请 SystemUI 作用域、
 * 手动热加载两个必要入口；不再保留模板自带的远程文件/随机配置测试按钮。</p>
 */
@SuppressLint("SetTextI18n")
class MainActivity : Activity(), App.ServiceStateListener {
    /** 当前 LSPosed 服务；为 null 表示尚未绑定。 */
    private var service: XposedService? = null

    /** ViewBinding 生成的界面绑定。 */
    private lateinit var binding: ActivityMainBinding

    /** 作用域申请结果回调。 */
    private val scopeCallback = object : OnScopeEventListener {
        /** 作用域申请通过。 */
        override fun onScopeRequestApproved(approved: List<String>) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "作用域申请通过：$approved",
                    Toast.LENGTH_SHORT
                ).show()
                binding.scope.text = "作用域：${service?.scope}"
            }
        }

        /** 作用域申请失败。 */
        override fun onScopeRequestFailed(message: String) {
            runOnUiThread {
                Toast.makeText(
                    this@MainActivity,
                    "作用域申请失败：$message",
                    Toast.LENGTH_SHORT
                ).show()
                binding.scope.text = "作用域：${service?.scope}"
            }
        }
    }

    /** 创建界面并绑定按钮。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.binder.text = "正在连接 LSPosed…"

        binding.requestScope.setOnClickListener {
            service?.requestScope(listOf(Constants.PACKAGE), scopeCallback)
        }
        binding.reload.setOnClickListener {
            hotReload()
        }
    }

    /** 页面可见时开始监听服务状态。 */
    override fun onStart() {
        super.onStart()
        App.addServiceStateListener(this, true)
    }

    /** 页面不可见时移除监听。 */
    override fun onStop() {
        App.removeServiceStateListener(this)
        super.onStop()
    }

    /** 服务绑定/断开时刷新界面。 */
    override fun onServiceStateChanged(service: XposedService?) {
        this.service = service
        runOnUiThread {
            if (service == null) {
                binding.binder.text = "Binder 未连接"
                binding.reload.isEnabled = false
                return@runOnUiThread
            }

            binding.binder.text = "Binder 已连接"
            binding.api.text = "API ${service.apiVersion}"
            binding.framework.text = "框架 ${service.frameworkName}"
            binding.frameworkVersion.text = "框架版本 ${service.frameworkVersion}"
            binding.frameworkVersionCode.text = "框架版本号 ${service.frameworkVersionCode}"

            val capabilities = buildList {
                val props = service.frameworkProperties
                if (props and XposedService.PROP_CAP_SYSTEM != 0L) add("SYSTEM")
                if (props and XposedService.PROP_CAP_REMOTE != 0L) add("REMOTE")
                if (props and XposedService.PROP_RT_API_PROTECTION != 0L) add("RT_API")
            }
            binding.frameworkProperties.text = "框架能力：$capabilities"
            binding.scope.text = "作用域：${service.scope}"

            val targets = service.runningTargets
            binding.process.text = "运行目标：${targets.map { it.processName }}"
            binding.reload.isEnabled = service.apiVersion >= 102
        }
    }

    /** 对所有正在运行的模块目标发起热加载。 */
    private fun hotReload() {
        val targetService = service ?: return
        if (targetService.apiVersion < 102) return

        for (target in targetService.runningTargets) {
            targetService.hotReloadModule(target, null) { _, result ->
                Log.i(Constants.TAG, "hot reload: $result")
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "热加载：$result", Toast.LENGTH_LONG).show()
                }
            }
        }
    }
}
