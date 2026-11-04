package cc.xiaowine.nmm.pager.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import kotlin.math.roundToInt

/**
 * 底部居中的轮播圆点。
 *
 * <p>自身负责在父容器底部居中定位，父容器布局变化时通过 OnGlobalLayoutListener
 * 重新计算 translation，因此不依赖 ConstraintLayout 的具体约束，也不会被系统的
 * ConstraintSet.applyTo() 重置。</p>
 */
internal class PagerDotsView(context: Context) : View(context) {
    /** 绘制圆点的画笔。 */
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 单个圆点半径（px）。 */
    private val radius = dp(2.5f)

    /** 相邻圆点间距（px）。 */
    private val gap = dp(4f)

    /** 距离卡片底部的边距（px）。 */
    private val bottomMargin = dp(6f)

    /** 当前页数量。 */
    private var pageCount = 0

    /** 当前页索引。 */
    private var pageIndex = 0

    /** 活动圆点的动画位置，允许小数，用于在圆点之间平滑移动。 */
    private var animatedIndex = 0f

    /** 活动圆点切换动画。 */
    private var animator: ValueAnimator? = null

    /** 父容器布局完成后重新定位圆点。 */
    private val layoutListener = ViewTreeObserver.OnGlobalLayoutListener {
        positionInParent()
    }

    /** 是否已经注册全局布局监听。 */
    private var observingLayout = false

    init {
        tag = DOTS_TAG
        isClickable = false
        isFocusable = false
        isLongClickable = false
    }

    /** 更新页数和当前页；页数不变且索引变化时播放活动圆点滑动动画。 */
    fun setPages(count: Int, index: Int) {
        val safeCount = count.coerceAtLeast(0)
        val safeIndex = index.coerceIn(0, (safeCount - 1).coerceAtLeast(0))

        // 页数变化时不对旧位置做动画，直接重建圆点。
        if (safeCount != pageCount) {
            animator?.cancel()
            pageCount = safeCount
            pageIndex = safeIndex
            animatedIndex = safeIndex.toFloat()
            requestLayout()
            invalidate()
            return
        }

        if (safeIndex == pageIndex) return
        pageIndex = safeIndex

        if (safeCount <= 1 || !isAttachedToWindow) {
            animator?.cancel()
            animatedIndex = safeIndex.toFloat()
            invalidate()
            return
        }
        animateTo(safeIndex)
    }

    /** 把活动圆点从当前位置动画到 target 索引。 */
    private fun animateTo(target: Int) {
        animator?.cancel()
        val start = animatedIndex
        val end = target.toFloat()
        if (start == end) return

        animator = ValueAnimator.ofFloat(start, end).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                animatedIndex = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    /** 按圆点数量测量自身宽度，高度固定为一个圆点直径。 */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (pageCount <= 1) {
            0
        } else {
            (pageCount * radius * 2 + (pageCount - 1) * gap).roundToInt()
        }
        val height = (radius * 2).roundToInt()
        setMeasuredDimension(
            resolveSize(width, widthMeasureSpec),
            resolveSize(height, heightMeasureSpec)
        )
    }

    /** 绘制所有非活动圆点，再在动画位置覆盖活动圆点。 */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (pageCount <= 1) return

        val centerY = height / 2f

        // 先画所有非活动圆点。
        paint.color = 0x66FFFFFF
        for (index in 0 until pageCount) {
            canvas.drawCircle(dotCenterX(index.toFloat()), centerY, radius, paint)
        }

        // 再用活动色覆盖当前/过渡位置，形成滑动切换动画。
        paint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(dotCenterX(animatedIndex), centerY, radius * 1.15f, paint)
    }

    /** 第 index 个圆点的中心 x；index 允许小数，用于动画插值。 */
    private fun dotCenterX(index: Float): Float =
        radius + index * (radius * 2 + gap)

    /** 自身尺寸变化后重新定位。 */
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        positionInParent()
    }

    /** 附着到窗口后开始监听父容器布局。 */
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animatedIndex = pageIndex.toFloat()
        if (!observingLayout) {
            viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            observingLayout = true
        }
        positionInParent()
    }

    /** 从窗口移除时取消动画和布局监听，避免泄漏。 */
    override fun onDetachedFromWindow() {
        if (observingLayout) {
            val observer = viewTreeObserver
            if (observer.isAlive) {
                observer.removeOnGlobalLayoutListener(layoutListener)
            }
            observingLayout = false
        }
        animator?.cancel()
        animator = null
        super.onDetachedFromWindow()
    }

    /**
     * 把自身移动到父容器底部居中。
     *
     * <p>父容器可能是 ConstraintLayout，系统重新应用约束后子 View 的 left/top 会变化，
     * 这里用 translation 抵消布局位置，始终保持在底部居中。</p>
     */
    private fun positionInParent() {
        val parent = parent as? ViewGroup ?: return
        if (width == 0 || height == 0 || parent.width == 0 || parent.height == 0) return

        translationX = (parent.width - width) / 2f - left
        translationY = parent.height - height - bottomMargin - top
    }

    /** dp 转像素。 */
    private fun dp(value: Float): Float =
        value * context.resources.displayMetrics.density

    companion object {
        /** 用于热加载/重建时识别圆点 View 的 tag。 */
        const val DOTS_TAG = "MiuiMediaPagerDots"
    }
}
