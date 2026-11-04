package cc.xiaowine.nmm.pager.util

import java.lang.reflect.Field
import java.lang.reflect.Method

/**
 * 轻量反射工具。
 *
 * <p>目标类全部来自 SystemUI APK，通过目标 ClassLoader 加载。所有查找都吞掉异常
 * 并返回 null/false，让单个字段或方法缺失时只降级，不影响 SystemUI 启动。</p>
 */
internal object Reflect {
    /** 使用指定 ClassLoader 加载类；失败返回 null。 */
    fun loadClass(name: String, classLoader: ClassLoader): Class<*>? = try {
        Class.forName(name, false, classLoader)
    } catch (_: Throwable) {
        null
    }

    /** 查找当前类声明的方法并设为可访问；不会向父类递归。 */
    fun method(type: Class<*>?, name: String, vararg parameters: Class<*>): Method? {
        if (type == null) return null
        return try {
            type.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
        } catch (_: Throwable) {
            null
        }
    }

    /** 沿类继承链查找方法；用于调用父类实现或 final 方法。 */
    fun findMethod(type: Class<*>?, name: String, vararg parameters: Class<*>): Method? {
        var current: Class<*>? = type
        while (current != null) {
            try {
                return current.getDeclaredMethod(name, *parameters).apply { isAccessible = true }
            } catch (_: NoSuchMethodException) {
                // 当前类没有，继续查父类。
            } catch (_: Throwable) {
                return null
            }
            current = current.superclass
        }
        return null
    }

    /** 沿类继承链读取字段值；字段不存在或不可访问时返回 null。 */
    fun getField(target: Any?, name: String): Any? {
        if (target == null) return null
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field: Field = type.getDeclaredField(name)
                field.isAccessible = true
                return field.get(target)
            } catch (_: NoSuchFieldException) {
                // 当前类没有，继续查父类。
            } catch (_: Throwable) {
                return null
            }
            type = type.superclass
        }
        return null
    }

    /** 沿类继承链写入字段值；成功返回 true，字段不存在或不可写返回 false。 */
    fun setField(target: Any?, name: String, value: Any?): Boolean {
        if (target == null) return false
        var type: Class<*>? = target.javaClass
        while (type != null) {
            try {
                val field = type.getDeclaredField(name)
                field.isAccessible = true
                field.set(target, value)
                return true
            } catch (_: NoSuchFieldException) {
                // 当前类没有，继续查父类。
            } catch (_: Throwable) {
                return false
            }
            type = type.superclass
        }
        return false
    }

    /** 读取布尔字段；字段缺失或不是布尔值时按 false 处理。 */
    fun boolField(target: Any?, name: String): Boolean =
        getField(target, name) as? Boolean ?: false
}
