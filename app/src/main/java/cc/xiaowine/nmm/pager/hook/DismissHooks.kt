package cc.xiaowine.nmm.pager.hook

import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSession
import android.util.Log
import io.github.libxposed.api.XposedModule
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.MediaManagerHandle
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * 媒体卡删除时的暂停逻辑。
 *
 * <p>原厂菜单项只暂停当前 topMediaData 对应的 session。这里在
 * LegacyMediaDataManagerImpl.onSwipeToDismiss()（媒体卡删除的统一出口）之前，
 * 遍历 mediaEntries，把所有 isPlaying=true 的 session 都暂停。</p>
 */
internal object DismissHooks {
    /** 安装删除暂停 hook；类或方法缺失时返回 false。 */
    fun install(module: XposedModule, classLoader: ClassLoader): Boolean {
        val managerClass = Reflect.loadClass(Constants.LEGACY_MEDIA_MANAGER, classLoader)
            ?: return false
        val method = Reflect.method(managerClass, "onSwipeToDismiss") ?: return false

        module.hook(method).intercept { chain ->
            // 卡片即将消失，清除手动选择，下一次媒体卡出现时重新跟随系统 top。
            PagerRuntime.selectedKey = null
            chain.thisObject?.let { pauseAllMedia(MediaManagerHandle(it)) }
            chain.proceed()
        }
        return true
    }

    /** 遍历 MediaDataManager 维护的 mediaEntries，只暂停正在播放的会话。 */
    private fun pauseAllMedia(manager: MediaManagerHandle) {
        val entries = Reflect.getField(manager.raw, "mediaEntries") as? Map<*, *> ?: return
        val context = Reflect.getField(manager.raw, "context") as? Context ?: return

        val pausedTokens = HashSet<MediaSession.Token>()
        for (value in entries.values) {
            if (value == null) continue
            if (Reflect.getField(value, "isPlaying") != true) continue
            val token = Reflect.getField(value, "token") as? MediaSession.Token ?: continue
            if (!pausedTokens.add(token)) continue

            try {
                MediaController(context, token).transportControls.pause()
            } catch (t: Throwable) {
                Log.w(Constants.TAG, "pause media on dismiss failed", t)
            }
        }

        Log.i(Constants.TAG, "media card dismissed, paused ${pausedTokens.size} session(s)")
    }
}
