package cc.xiaowine.nmm.pager.gesture

import android.annotation.SuppressLint
import android.util.Log
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.MediaPagerNavigator
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SwipeHelperHandle
import cc.xiaowine.nmm.pager.util.Reflect
import kotlin.math.abs

/**
 * 接管通知栈原本就有的横向手势，不额外添加触摸层。
 *
 * <p>MIUI 的媒体卡横向滑动原本由 SwipeHelper 处理，用于打开“常驻/删除”菜单。
 * 这里观察同一个手势，并按按下位置分流：</p>
 *
 * - 左右各 15% 的边缘区域：保留完整位移，交还原侧滑菜单逻辑；
 * - 中间 70% 区域：卡片只做阻尼跟手，松手达到阈值后循环翻页；
 * - 单会话媒体卡和普通通知行：完全不介入。
 *
 * <p>手势状态按一次 DOWN→UP/CANCEL 保存，并在结束、取消或新按下时彻底清理。</p>
 */
@SuppressLint("StaticFieldLeak")
internal object MediaPagerGesture {
    /** 未判定：刚按下或没有产生横向位移。 */
    private const val MODE_NONE = 0

    /** 翻页态：中间区域起滑。 */
    private const val MODE_PAGE = 1

    /** 边缘态：边缘区域起滑，交还原侧滑菜单。 */
    private const val MODE_EDGE = 2

    /** 翻页时卡片跟手阻尼：手指移动 100px，卡片移动 8px。 */
    private const val DAMPING = 0.08f

    /** 翻页提交阈值：累计位移超过卡片宽度的 18%。 */
    private const val PAGE_THRESHOLD = 0.18f

    /** 快速滑动阈值（px/s）：速度超过它时，即使位移较小也提交翻页。 */
    private const val FLING_VELOCITY = 1200f

    /** 边缘起滑区宽度比例：左右各 15%。 */
    private const val EDGE_ZONE = 0.15f

    /** 当前手势是否已确认为媒体卡横向手势。 */
    private var active = false

    /** 触发本次手势的 SwipeHelper 句柄，避免其他 SwipeHelper 的事件误清理状态。 */
    private var owner: SwipeHelperHandle? = null

    /** 当前手势对应的媒体卡根 View。 */
    private var header: View? = null

    /** 最近一次累计横向位移；正数向右，负数向左。 */
    private var lastDx = 0f

    /** 当前手势模式。 */
    private var mode = MODE_NONE

    /** 本次手势是否从左右边缘区域开始。 */
    private var edgeStart = false

    /**
     * 由 NotificationSwipeHelper.onInterceptTouchEvent 的 ACTION_DOWN 调用。
     *
     * @param swipeHelper 当前 SwipeHelper 句柄
     * @param card 媒体卡根 View
     * @param localX 手指相对卡片左边缘的 x 坐标
     * @param pageCount 当前媒体会话数
     */
    fun onDown(swipeHelper: SwipeHelperHandle, card: View, localX: Float, pageCount: Int) {
        if (pageCount <= 1) {
            resetState()
            return
        }

        val width = card.width.coerceAtLeast(1)
        val edgeWidth = width * EDGE_ZONE
        val nextEdgeStart = localX <= edgeWidth || localX >= width - edgeWidth

        // 上一次松手后的 snap 动画可能还在跑；先取消，避免它在本手势中继续改位移。
        cancelPendingAnimations(swipeHelper, card)

        // 上一次边缘手势可能把卡片留在完整位移/菜单打开状态。中间起滑前先归零，
        // 否则 SwipeHelper.mTranslation 会带着旧的大位移进入本次手势，第一帧就大跳。
        if (!nextEdgeStart) {
            resetTranslation(card)
            clearHelperTranslation(swipeHelper)
        } else if (active && mode == MODE_PAGE) {
            resetTranslation(header)
            clearHelperTranslation(swipeHelper)
        }
        resetState()

        active = true
        owner = swipeHelper
        header = card
        edgeStart = nextEdgeStart
        mode = MODE_NONE
    }

    /**
     * 由 NotificationSwipeHelper.setTranslation 的 hook 调用。
     *
     * @return 非 null 时替换系统位移实现阻尼；null 表示完全放行（边缘菜单）。
     */
    fun onTranslation(
        swipeHelper: SwipeHelperHandle,
        view: View?,
        translation: Float
    ): Float? {
        if (!active || view == null || view !== header || swipeHelper != owner) return null

        lastDx = translation
        if (edgeStart) {
            mode = MODE_EDGE
            return null
        }

        mode = MODE_PAGE
        return translation * DAMPING
    }

    /**
     * 由 SwipeHelper.onTouchEvent 的 hook 在系统处理完事件后调用。
     *
     * <p>正常情况下翻页已在 handleMenuRowSwipe 中完成；如果该 hook 没有命中
     * （例如 ROM 版本差异），这里用位移阈值补一次提交，避免手势无响应。</p>
     */
    fun onTouch(swipeHelper: SwipeHelperHandle, event: MotionEvent) {
        if (!active || swipeHelper != owner) return

        when (event.actionMasked) {
            MotionEvent.ACTION_UP -> {
                if (mode == MODE_PAGE) {
                    commitPage(velocity = null)
                    resetTranslation(header)
                    clearHelperTranslation(swipeHelper)
                }
                resetState()
            }

            MotionEvent.ACTION_CANCEL -> {
                if (mode == MODE_PAGE) {
                    resetTranslation(header)
                    clearHelperTranslation(swipeHelper)
                }
                resetState()
            }
        }
    }

    /**
     * 由 MiuiNotificationSwipeHelper.handleMenuRowSwipe 的 hook 在系统处理前调用。
     *
     * @return true 表示本次 UP/CANCEL 已被翻页逻辑消费，必须跳过原侧滑菜单。
     */
    fun onMenuRowSwipe(
        swipeHelper: SwipeHelperHandle,
        view: View?,
        velocity: Float,
        event: MotionEvent
    ): Boolean {
        if (!active || header == null || view !== header || swipeHelper != owner) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_CANCEL -> {
                val consume = mode == MODE_PAGE
                if (consume) {
                    resetTranslation(header)
                    clearHelperTranslation(swipeHelper)
                }
                resetState()
                return consume
            }

            MotionEvent.ACTION_UP -> {
                when (mode) {
                    MODE_PAGE -> {
                        commitPage(velocity)
                        resetTranslation(header)
                        clearHelperTranslation(swipeHelper)
                        resetState()
                        // 中间区域的所有 UP 都消费掉，避免松手后误开侧滑菜单。
                        return true
                    }

                    MODE_EDGE -> {
                        // 边缘手势交还原逻辑打开“常驻/删除”菜单。
                        resetState()
                        return false
                    }

                    else -> {
                        resetState()
                        return false
                    }
                }
            }
        }
        return false
    }

    /** 根据累计位移或速度提交翻页；成功时触发一次系统触感反馈。 */
    private fun commitPage(velocity: Float?) {
        val card = header ?: return
        val controller = PagerRuntime.controller ?: return

        val width = card.width.coerceAtLeast(1)
        val fast = velocity != null && abs(velocity) > FLING_VELOCITY
        val farEnough = abs(lastDx) > width * PAGE_THRESHOLD
        if (!fast && !farEnough) return

        val direction = when {
            lastDx > 0f -> -1
            lastDx < 0f -> 1
            velocity != null && velocity > 0f -> -1
            else -> 1
        }

        if (MediaPagerNavigator.page(controller, direction)) {
            // performHapticFeedback 遵守系统触感设置，不需要 VIBRATE 权限。
            card.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }
    }

    /** 调用 MiuiMediaHeaderView.resetTranslation()，把阻尼位移归零。 */
    private fun resetTranslation(view: View?) {
        if (view == null) return
        val method = Reflect.findMethod(view.javaClass, "resetTranslation")
        try {
            method?.invoke(view)
        } catch (t: Throwable) {
            Log.w(Constants.TAG, "resetTranslation failed", t)
        }
    }

    /** 清掉 SwipeHelper 内部累积的原始位移，避免下一次手势从旧位移继续。 */
    private fun clearHelperTranslation(swipeHelper: SwipeHelperHandle) {
        Reflect.setField(swipeHelper.raw, "mTranslation", 0f)
    }

    /** 取消上一次松手后可能仍在运行的 snap/dismiss/translate 动画。 */
    private fun cancelPendingAnimations(swipeHelper: SwipeHelperHandle, card: View) {
        val raw = swipeHelper.raw
        cancelAnimator(Reflect.getField(raw, "mSnapChildAnim"))
        val pending = Reflect.getField(raw, "mDismissPendingMap") as? Map<*, *>
        cancelAnimator(pending?.get(card))
        cancelAnimator(Reflect.getField(card, "translateAnim"))
    }

    /** 通过反射调用目标的 cancel()。 */
    private fun cancelAnimator(target: Any?) {
        if (target == null) return
        try {
            Reflect.findMethod(target.javaClass, "cancel")?.invoke(target)
        } catch (_: Throwable) {
            // 动画对象可能已结束，忽略。
        }
    }

    /** 清空一次手势的全部临时状态。 */
    private fun resetState() {
        active = false
        owner = null
        header = null
        lastDx = 0f
        mode = MODE_NONE
        edgeStart = false
    }
}
