# LSPosed Modern API 会通过 META-INF/xposed/java_init.list 反射创建模块类。
# 允许混淆类名，但保留全部成员，并让 R8 同步改写 java_init.list。
-keep,allowoptimization,allowobfuscation public class * extends io.github.libxposed.api.XposedModule {
    *;
}
-adaptresourcefilecontents META-INF/xposed/java_init.list

# Manifest 中声明的组件必须保留原始类名。
-keep class cc.xiaowine.nmm.App { *; }
-keep class cc.xiaowine.nmm.MainActivity { *; }

# 圆点类名参与旧 generation 的清理判断；保留名字，避免热加载后残留。
-keepnames class cc.xiaowine.nmm.pager.ui.PagerDotsView

-dontwarn io.github.libxposed.annotation.**
