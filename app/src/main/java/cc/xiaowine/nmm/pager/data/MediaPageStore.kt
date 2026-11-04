package cc.xiaowine.nmm.pager.data

import cc.xiaowine.nmm.pager.ControllerHandle
import cc.xiaowine.nmm.pager.MediaCard
import cc.xiaowine.nmm.pager.MediaDataHandle
import cc.xiaowine.nmm.pager.PagerRuntime
import cc.xiaowine.nmm.pager.SortUtilsHandle
import cc.xiaowine.nmm.pager.util.Reflect

/**
 * 媒体会话列表读取器。
 *
 * <p>优先读取控制器自己的 MediaSortUtils.mediaDataList，它已经是媒体卡认可的
 * 会话集合并按小米排序器排好。只有排序集合为空时才回退到
 * MediaDataManager.mediaEntries，并过滤 active=false 的条目。</p>
 */
internal object MediaPageStore {
    /** 返回当前可翻页的 MediaData 列表，顺序与媒体卡排序一致。 */
    fun pages(controller: ControllerHandle?): List<MediaDataHandle> {
        val rawController = controller?.raw ?: return emptyList()

        // 用有序 Map 去重，避免排序集合偶发出现同一会话的两条记录。
        val result = LinkedHashMap<String, MediaDataHandle>()
        val sortUtils = Reflect.getField(rawController, "mediaSorUtils")
        val sorted = Reflect.getField(sortUtils, "mediaDataList")
        if (sorted is Iterable<*>) {
            for (sortKey in sorted) {
                val data = Reflect.getField(sortKey, "data")
                if (isMediaData(data)) {
                    result[keyOfRaw(data)] = MediaDataHandle(data!!)
                }
            }
        }

        // 少数时序下排序集合还没填充，用 MediaDataManager 的 map 兜底。
        if (result.isEmpty()) {
            val manager = Reflect.getField(rawController, "mediaDataManager")
            val entries = findMediaDataMap(manager)
            if (entries != null) {
                for (value in entries.values) {
                    if (isMediaData(value) && Reflect.boolField(value, "active")) {
                        result[keyOfRaw(value)] = MediaDataHandle(value!!)
                    }
                }
            }
        }
        return result.values.toList()
    }

    /** 按 key 查找最新的 MediaData 句柄；找不到返回 null。 */
    fun findPage(controller: ControllerHandle?, key: String?): MediaDataHandle? {
        val all = pages(controller)
        val index = indexOf(all, key)
        return if (index >= 0) all[index] else null
    }

    /** 直接在 MediaSortUtils 的排序集合中按 key 查找，避免触发 controller 回退逻辑。 */
    fun findPageInSort(sortUtils: SortUtilsHandle?, key: String?): MediaDataHandle? {
        if (key == null || sortUtils == null) return null
        val sorted = Reflect.getField(sortUtils.raw, "mediaDataList")
        if (sorted !is Iterable<*>) return null
        for (sortKey in sorted) {
            val data = Reflect.getField(sortKey, "data")
            if (data != null && key == keyOfRaw(data)) return MediaDataHandle(data)
        }
        return null
    }

    /** 返回 key 在列表中的索引，找不到返回 -1。 */
    fun indexOf(pages: List<MediaDataHandle>, key: String?): Int {
        if (key == null) return -1
        for (index in pages.indices) {
            if (key == keyOf(pages[index])) return index
        }
        return -1
    }

    /**
     * 返回当前应显示的页索引。
     *
     * <p>优先使用手动选中的 key；没有手动选择时跟随系统 topMediaData。
     * 两者都找不到时按第一页处理。</p>
     */
    fun indexOfCurrent(controller: ControllerHandle?, pages: List<MediaDataHandle>): Int {
        if (pages.isEmpty()) return 0

        val selectedIndex = indexOf(pages, PagerRuntime.selectedKey)
        if (selectedIndex >= 0) return selectedIndex

        val topIndex = indexOf(pages, keyOf(MediaCard.topData(controller)))
        return if (topIndex >= 0) topIndex else 0
    }

    /** 清除已经不在列表中的 selectedKey，避免后续翻页使用过期页。 */
    fun normalizeSelection(pages: List<MediaDataHandle>) {
        val selected = PagerRuntime.selectedKey ?: return
        if (indexOf(pages, selected) < 0) {
            PagerRuntime.selectedKey = null
        }
    }

    /** 生成稳定的页标识：优先 notificationKey，缺失时用包名/mediaId/instanceId 组合。 */
    fun keyOf(data: MediaDataHandle?): String = keyOfRaw(data?.raw)

    /** 原始对象的 key 计算，仅供本对象内部和反射边界使用。 */
    private fun keyOfRaw(data: Any?): String {
        if (data == null) return ""

        val notificationKey = Reflect.getField(data, "notificationKey")?.toString()
        if (!notificationKey.isNullOrEmpty() && notificationKey != "null") {
            return notificationKey
        }

        val packageName = Reflect.getField(data, "packageName")
        val mediaId = Reflect.getField(data, "mediaId")
        val instanceId = Reflect.getField(data, "instanceId")
        return "$packageName#$mediaId#$instanceId"
    }

    /** 判断对象是否是 MediaData；类型尚未解析时按非空处理。 */
    private fun isMediaData(value: Any?): Boolean {
        if (value == null) return false
        val type = PagerRuntime.classes?.mediaData ?: return true
        return type.isInstance(value)
    }

    /** 在 MediaDataManager 实现中寻找 value 为 MediaData 的 Map 字段。 */
    private fun findMediaDataMap(manager: Any?): Map<*, *>? {
        if (manager == null) return null

        var fallback: Map<*, *>? = null
        var type: Class<*>? = manager.javaClass
        while (type != null) {
            for (field in type.declaredFields) {
                if (!Map::class.java.isAssignableFrom(field.type)) continue
                try {
                    field.isAccessible = true
                    val value = field.get(manager) as? Map<*, *> ?: continue
                    if (value.values.none { isMediaData(it) }) continue
                    if (field.name == "mediaEntries") return value
                    fallback = value
                } catch (_: Throwable) {
                    // 不可访问的字段直接跳过。
                }
            }
            type = type.superclass
        }
        return fallback
    }
}
