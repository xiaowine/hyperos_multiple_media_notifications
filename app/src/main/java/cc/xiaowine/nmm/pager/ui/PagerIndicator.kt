package cc.xiaowine.nmm.pager.ui

import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.ControllerHandle
import cc.xiaowine.nmm.pager.MediaCard
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.data.MediaPageStore

/**
 * 媒体卡圆点指示器的安装、刷新与清理。
 *
 * <p>指示器挂在 mediaViewHolder.player 内部。player 是 ConstraintLayout 时
 * 使用其 LayoutParams 添加；圆点自身负责底部居中定位，因此这里只管理生命周期和页码。</p>
 */
internal object PagerIndicator {
    /** 异步刷新；用于 hook 回调中避免直接操作 UI 线程。 */
    fun refreshLater(controller: ControllerHandle?) {
        if (controller == null) return
        PagerRuntime.onMain { refresh(controller) }
    }

    /** 根据当前 controller 的媒体列表刷新圆点。 */
    fun refresh(controller: ControllerHandle?) {
        if (controller == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            refreshLater(controller)
            return
        }

        val player = MediaCard.player(controller) ?: return
        var dots = findDots(player)
        if (dots == null) {
            // 热加载后旧 generation 的圆点 tag 还在，但类型已经不同，先整体清掉。
            removeIndicators(player)
            dots = PagerDotsView(player.context)
            if (!addDots(player, dots)) return
        }

        val pages = MediaPageStore.pages(controller)
        MediaPageStore.normalizeSelection(pages)

        if (pages.size <= 1) {
            dots.setPages(pages.size, 0)
            dots.visibility = View.GONE
        } else {
            val index = MediaPageStore.indexOfCurrent(controller, pages)
            dots.setPages(pages.size, index)
            dots.contentDescription = "媒体页 ${index + 1}/${pages.size}"
            dots.visibility = View.VISIBLE
        }
        dots.bringToFront()
    }

    /** 热加载/重建时清除旧 generation 的指示器，避免重复叠加。 */
    fun cleanup(controller: ControllerHandle?) {
        if (controller == null) return
        if (Looper.myLooper() != Looper.getMainLooper()) {
            PagerRuntime.onMain { cleanup(controller) }
            return
        }

        removeIndicators(MediaCard.player(controller))
        removeIndicators(MediaCard.card(controller) as? ViewGroup)
    }

    /** 从指定父容器中移除当前/旧版指示器。 */
    private fun removeIndicators(parent: ViewGroup?) {
        if (parent == null) return
        for (index in parent.childCount - 1 downTo 0) {
            val child = parent.getChildAt(index)
            if (isIndicator(child)) {
                parent.removeView(child)
            }
        }
    }

    /** 判断一个子 View 是否是指示器；类名判断兼容热加载前的旧 generation。 */
    private fun isIndicator(view: View): Boolean =
        view.tag == PagerDotsView.DOTS_TAG ||
            view.javaClass.name.contains("PagerDotsView")

    /** 在 player 中查找当前 generation 的圆点。 */
    private fun findDots(player: ViewGroup): PagerDotsView? {
        for (index in 0 until player.childCount) {
            val child = player.getChildAt(index)
            if (child is PagerDotsView && child.tag == PagerDotsView.DOTS_TAG) {
                return child
            }
        }
        return null
    }

    /** 把圆点加入 player；ConstraintLayout 使用其 LayoutParams，其他父容器使用 FrameLayout。 */
    private fun addDots(player: ViewGroup, dots: PagerDotsView): Boolean {
        return try {
            player.addView(dots, layoutParamsFor(player))
            true
        } catch (t: Throwable) {
            Log.e(Constants.TAG, "add dots failed", t)
            false
        }
    }

    /** 根据父容器类型创建合适的 LayoutParams。 */
    private fun layoutParamsFor(player: ViewGroup): ViewGroup.LayoutParams {
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        if (player.javaClass.name != "androidx.constraintlayout.widget.ConstraintLayout") {
            return FrameLayout.LayoutParams(wrap, wrap)
        }

        val paramsClass = Class.forName(
            "androidx.constraintlayout.widget.ConstraintLayout\$LayoutParams",
            false,
            player.javaClass.classLoader
        )
        val constructor = paramsClass.getConstructor(
            Int::class.javaPrimitiveType!!,
            Int::class.javaPrimitiveType!!
        )
        return constructor.newInstance(wrap, wrap) as ViewGroup.LayoutParams
    }
}
