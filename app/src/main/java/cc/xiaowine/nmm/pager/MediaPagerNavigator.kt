package cc.xiaowine.nmm.pager

import android.os.Looper
import android.util.Log
import cc.xiaowine.nmm.pager.data.MediaPageStore
import cc.xiaowine.nmm.pager.ui.PagerIndicator

/**
 * 媒体分页状态机。
 *
 * <p>系统媒体卡同一时间只绑定一个 MediaData，因此翻页不创建第二套 View，
 * 而是更新 selectedKey，把新 MediaData 通过原厂 setTopMediaData 重新绑定到当前卡。
 * 这样 seekbar、语义按钮、封面动画和点击 Intent 都继续沿用原实现。</p>
 */
internal object MediaPagerNavigator {
    /**
     * 切换到相对当前页偏移 delta 的页。
     *
     * <p>使用 floorMod 实现首尾循环：第一页向右到最后一页，最后一页向左到第一页。</p>
     *
     * @return 是否真正完成切换
     */
    fun page(controller: ControllerHandle?, delta: Int): Boolean {
        if (controller == null) return false
        if (Looper.myLooper() != Looper.getMainLooper()) {
            PagerRuntime.onMain { page(controller, delta) }
            return false
        }

        val pages = MediaPageStore.pages(controller)
        if (pages.size < 2) return false

        val current = MediaPageStore.indexOfCurrent(controller, pages)
        val next = Math.floorMod(current + delta, pages.size)
        val data = pages[next]
        val previousKey = PagerRuntime.selectedKey

        PagerRuntime.selectedKey = MediaPageStore.keyOf(data)
        if (!MediaCard.setTop(controller, data)) {
            PagerRuntime.selectedKey = previousKey
            Log.w(Constants.TAG, "page failed, restore selection")
            return false
        }

        // setTopMediaData 的 hook 也会刷新，这里主动刷新一次保证 fallback 路径同样更新圆点。
        PagerIndicator.refreshLater(controller)
        Log.d(
            Constants.TAG,
            "page ${next + 1}/${pages.size} key=${PagerRuntime.selectedKey}"
        )
        return true
    }
}
