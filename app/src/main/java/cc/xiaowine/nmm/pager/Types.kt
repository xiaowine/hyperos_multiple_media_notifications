package cc.xiaowine.nmm.pager

/**
 * 反射边界之外使用的强类型句柄。
 *
 * <p>SystemUI 的目标类不在模块编译路径中，无法直接声明为真实类型；如果全部用
 * Any，controller、MediaData、SwipeHelper 之间可以互相误传。这里用零开销的
 * value class 包一层，只在真正调用反射时取出 [raw]。</p>
 */

/** 媒体卡 NodeController 句柄。 */
@JvmInline
internal value class ControllerHandle(val raw: Any)

/** 单条媒体会话 MediaData 句柄。 */
@JvmInline
internal value class MediaDataHandle(val raw: Any)

/** SystemUI SwipeHelper 句柄，仅用于手势归属校验。 */
@JvmInline
internal value class SwipeHelperHandle(val raw: Any)

/** MediaSortUtils 句柄，用于按 key 直接查询排序集合。 */
@JvmInline
internal value class SortUtilsHandle(val raw: Any)

/** 旧版 MediaDataManager 句柄，仅用于删除时暂停会话。 */
@JvmInline
internal value class MediaManagerHandle(val raw: Any)
