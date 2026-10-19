# 个人工作台 APP - ProGuard 规则
# 包体积目标 < 30MB (PRD 6.1)，此处仅保留必要规则

# 保留 Room 实体与 DAO
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.example.personalworkbench.data.local.entities.** { *; }

# 保留 DataStore 序列化数据类
-keep class com.example.personalworkbench.data.** { *; }

# 保留 ViewModel（类 + 全部构造，Factory 直接 new，防止构造被优化导致启动崩溃）
-keep class * extends androidx.lifecycle.ViewModel { <init>(...); }
-keep class com.example.personalworkbench.viewmodel.** { *; }

# 保留枚举（视图模式/重复频率/主题模式等，when 分支与 entries 遍历依赖）
-keepclassmembers enum com.example.personalworkbench.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
    <fields>;
    <methods>;
}

# Compose / Kotlin 协程保留
-keepattributes *Annotation*
-keep class kotlin.coroutines.Continuation

# 保留带 @Keep 的类
-keep @androidx.annotation.Keep class *
-keepclasseswithmembers class * { @androidx.annotation.Keep <methods>; }
-keepclasseswithmembers class * { @androidx.annotation.Keep <fields>; }
