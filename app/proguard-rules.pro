# Cellier Manager — ProGuard rules
# Par défaut, on n'active pas minify en debug. Pour une release éventuelle :

-keep class org.jsoup.** { *; }
-keepattributes *Annotation*

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
