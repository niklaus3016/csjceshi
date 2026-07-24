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

# GroMore融合SDK混淆规则
-keep class com.pangle.** { *; }
-keep class com.bytedance.sdk.** { *; }
-keep class com.bytedance.cn.** { *; }
-keep class com.tt.** { *; }
-keep class com.qq.e.** { *; }
-keep class com.umeng.** { *; }
-keep class com.alibaba.sdk.android.oss.** { *; }
-keep class com.android.internal.os.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.exoplayer2.** { *; }
-keep class com.google.android.exoplayer.** { *; }
-keep class com.google.android.gsf.** { *; }
-keep class com.sigmob.** { *; }
-keep class com.kwad.sdk.** { *; }
-keep class com.baidu.mobads.** { *; }
-keep class com.tencent.bugly.** { *; }
-keep class com.kugou.** { *; }
-keep class com.growingio.android.** { *; }
-keep class com.appsflyer.** { *; }
-keep class com.tapjoy.** { *; }
-keep class com.adjust.sdk.** { *; }
-keep class com.facebook.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.google.android.ads.** { *; }
-keep class com.google.android.gms.** { *; }
-keep class com.google.android.apps.ads.** { *; }
-keep class com.google.android.apps.measurement.** { *; }
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.android.gms.measurement.** { *; }
-keep class com.google.android.measurement.** { *; }
-keep class com.google.android.ump.** { *; }
-keep class com.google.android.ads.consent.** { *; }
-keep class com.google.android.ads.consentform.** { *; }

# 穿山甲SDK需要反射调用的类
-keep public class * extends android.app.Activity
-keep public class * extends android.app.Application
-keep public class * extends android.content.BroadcastReceiver
-keep public class * extends android.content.ContentProvider
-keep public class * extends android.app.Service

# 保持JS接口不被混淆
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# 保持native方法不被混淆
-keepclasseswithmembernames class * {
    native <methods>;
}
