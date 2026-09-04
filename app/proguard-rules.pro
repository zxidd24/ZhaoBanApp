# MVP 阶段未开启混淆，保留默认规则占位
-keepattributes *Annotation*
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
