package cc.xiaowine.nmm.pager

/**
 * 模块共享常量。
 *
 * <p>集中保存日志 TAG、目标包名以及需要通过反射访问的 SystemUI 类名；
 * 类名全部使用完整限定名，避免依赖 SystemUI 的内部导入。</p>
 */
internal object Constants {
    /** Logcat 与 LSPosed 日志使用的 TAG。 */
    const val TAG = "MiuiMediaPager"

    /** 模块作用的系统界面包名。 */
    const val PACKAGE = "com.android.systemui"

    /** 媒体卡 NodeController。 */
    const val CONTROLLER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaNotificationControllerImpl"

    /** 单会话媒体卡绑定控制器。 */
    const val MEDIA_VIEW =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl"

    /** 媒体卡根 View（MiuiMediaHeaderView）。 */
    const val HEADER =
        "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaHeaderView"

    /** 小米维护的媒体会话排序工具。 */
    const val SORT_UTILS =
        "com.android.systemui.statusbar.notification.mediacontrol.MediaSortUtils"

    /** 通知栈基础 SwipeHelper，负责横滑生命周期。 */
    const val SWIPE_HELPER = "com.android.systemui.SwipeHelper"

    /** 通知 SwipeHelper，实际接管平移与侧滑菜单。 */
    const val NOTIFICATION_SWIPE_HELPER =
        "com.android.systemui.statusbar.notification.stack.NotificationSwipeHelper"

    /** MIUI 通知 SwipeHelper，媒体卡侧滑菜单在 handleMenuRowSwipe 中处理。 */
    const val MIUI_SWIPE_HELPER =
        "com.android.systemui.statusbar.notification.stack.MiuiNotificationSwipeHelper"

    /** 侧滑菜单行接口，用于精确匹配 handleMenuRowSwipe 的参数类型。 */
    const val MENU_ROW_PLUGIN =
        "com.android.systemui.plugins.statusbar.NotificationMenuRowPlugin"

    /** 单条媒体会话的运行时数据模型。 */
    const val MEDIA_DATA =
        "com.android.systemui.media.controls.shared.model.MediaData"

    /** 旧版 MediaDataManager 实现，onSwipeToDismiss 是媒体卡删除的统一出口。 */
    const val LEGACY_MEDIA_MANAGER =
        "com.android.systemui.media.controls.domain.pipeline.LegacyMediaDataManagerImpl"
}
