package cc.xiaowine.nmm.pager.hook

import android.util.Log
import android.view.MotionEvent
import android.view.View
import io.github.libxposed.api.XposedModule
import cc.xiaowine.nmm.pager.Constants
import cc.xiaowine.nmm.pager.MediaCard
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SwipeHelperHandle
import cc.xiaowine.nmm.pager.SystemUiClasses
import cc.xiaowine.nmm.pager.data.MediaPageStore
import cc.xiaowine.nmm.pager.gesture.MediaPagerGesture
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * 通知栈横向手势 hook。
 *
 * <p>不添加子 View 触摸层，而是复用 SwipeHelper 已有的手势生命周期：
 * onInterceptTouchEvent 记录起点，setTranslation 注入阻尼，onTouchEvent 兜底提交，
 * handleMenuRowSwipe 在 UP 时消费中间区域的翻页手势。</p>
 */
internal object SwipeHooks {
    /** 安装全部手势 hook；返回是否全部命中。 */
    fun install(module: XposedModule, classes: SystemUiClasses): Boolean {
        val touch = hookSwipeTouch(module, classes)
        val down = hookInterceptDown(module, classes)
        val translation = hookSetTranslation(module, classes)
        val menuRow = hookMenuRowSwipe(module, classes)

        module.log(
            Log.INFO,
            Constants.TAG,
            "gesture hooks: touch=$touch down=$down translation=$translation menuRow=$menuRow"
        )
        return touch && down && translation && menuRow
    }

    /** ACTION_DOWN 时记录媒体卡和按下位置，供后续手势分流。 */
    private fun hookInterceptDown(module: XposedModule, classes: SystemUiClasses): Boolean {
        val helperClass = classes.notificationSwipeHelper ?: return false
        val method = Reflect.method(
            helperClass,
            "onInterceptTouchEvent",
            MotionEvent::class.java
        ) ?: return false

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val event = chain.getArg(0) as? MotionEvent
            if (event?.actionMasked == MotionEvent.ACTION_DOWN) {
                val helperRaw = chain.thisObject
                val touched = Reflect.getField(helperRaw, "mTouchedView") as? View
                val controller = PagerRuntime.controller
                val card = MediaCard.card(controller)

                if (helperRaw != null && touched != null && card != null && touched === card) {
                    val pages = MediaPageStore.pages(controller)
                    if (pages.size > 1) {
                        val location = IntArray(2)
                        card.getLocationOnScreen(location)
                        MediaPagerGesture.onDown(
                            SwipeHelperHandle(helperRaw),
                            card,
                            event.rawX - location[0],
                            pages.size
                        )
                    }
                }
            }
            result
        }
        return true
    }

    /** 系统处理完 onTouchEvent 后维护 UP/CANCEL，必要时补提交翻页。 */
    private fun hookSwipeTouch(module: XposedModule, classes: SystemUiClasses): Boolean {
        val helperClass = classes.swipeHelper ?: return false
        val method = Reflect.method(helperClass, "onTouchEvent", MotionEvent::class.java)
            ?: return false

        module.hook(method).intercept { chain ->
            val result = chain.proceed()
            val event = chain.getArg(0) as? MotionEvent
            val helperRaw = chain.thisObject
            if (event != null && helperRaw != null) {
                MediaPagerGesture.onTouch(SwipeHelperHandle(helperRaw), event)
            }
            result
        }
        return true
    }

    /** 用阻尼位移替换系统位移；边缘手势返回 null 保持完整位移。 */
    private fun hookSetTranslation(module: XposedModule, classes: SystemUiClasses): Boolean {
        val helperClass = classes.notificationSwipeHelper ?: return false
        val method = Reflect.method(
            helperClass,
            "setTranslation",
            Float::class.javaPrimitiveType!!,
            View::class.java
        ) ?: return false

        module.hook(method).intercept { chain ->
            val view = chain.getArg(1) as? View
            val translation = chain.getArg(0) as? Float ?: 0f
            val helperRaw = chain.thisObject
            val damped = if (helperRaw == null) {
                null
            } else {
                MediaPagerGesture.onTranslation(
                    SwipeHelperHandle(helperRaw),
                    view,
                    translation
                )
            }

            if (damped != null && view != null) {
                // proceed 的参数数组由 Xposed API 定义为 Any[]，这里是最后一个反射边界。
                chain.proceed(arrayOf<Any>(damped, view))
            } else {
                chain.proceed()
            }
        }
        return true
    }

    /** UP/CANCEL 时消费中间区域的翻页手势，边缘手势放行原侧滑菜单。 */
    private fun hookMenuRowSwipe(module: XposedModule, classes: SystemUiClasses): Boolean {
        val helperClass = classes.miuiSwipeHelper ?: return false
        val menuRowClass = classes.menuRowPlugin ?: return false
        val method = Reflect.method(
            helperClass,
            "handleMenuRowSwipe",
            MotionEvent::class.java,
            View::class.java,
            Float::class.javaPrimitiveType!!,
            menuRowClass
        ) ?: return false

        module.hook(method).intercept { chain ->
            val event = chain.getArg(0) as? MotionEvent
            val view = chain.getArg(1) as? View
            val velocity = chain.getArg(2) as? Float ?: 0f
            val helperRaw = chain.thisObject
            if (event != null && view != null && helperRaw != null &&
                MediaPagerGesture.onMenuRowSwipe(
                    SwipeHelperHandle(helperRaw),
                    view,
                    velocity,
                    event
                )
            ) {
                null
            } else {
                chain.proceed()
            }
        }
        return true
    }
}
