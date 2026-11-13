# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ---- R8 裁剪安全规则 ----
# Gson 通过反射按字段名反序列化，数据模型必须保留（字段名/构造器不能被裁剪或混淆），
# 否则 Retrofit + converter-gson 在运行时解析 JSON 会失败或字段丢失。
-keepattributes Signature, *Annotation*
-keep class com.chuanwen.audiobookclien.data.** { *; }