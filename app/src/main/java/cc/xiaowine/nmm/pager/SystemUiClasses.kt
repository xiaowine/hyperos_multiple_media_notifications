package cc.xiaowine.nmm.pager

/**
 * 一次安装中解析出的 SystemUI 类型集合。
 *
 * <p>必需类型缺失时整个模块不安装；可选类型缺失时只关闭对应能力，
 * 例如 SwipeHelper 缺失时仍保留分页状态和圆点，但无法接收手势。</p>
 */
internal class SystemUiClasses(
    /** 媒体卡 NodeController 类型。 */
    val controller: Class<*>,
    /** 媒体卡绑定控制器类型。 */
    val mediaView: Class<*>,
    /** 媒体卡根 View 类型。 */
    val header: Class<*>,
    /** MediaData 类型。 */
    val mediaData: Class<*>,
    /** 媒体排序工具类型，可能缺失。 */
    val sortUtils: Class<*>?,
    /** 基础 SwipeHelper 类型，可能缺失。 */
    val swipeHelper: Class<*>?,
    /** 通知 SwipeHelper 类型，可能缺失。 */
    val notificationSwipeHelper: Class<*>?,
    /** MIUI 通知 SwipeHelper 类型，可能缺失。 */
    val miuiSwipeHelper: Class<*>?,
    /** 侧滑菜单行插件类型，可能缺失。 */
    val menuRowPlugin: Class<*>?
)
