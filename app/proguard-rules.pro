## Elimina logs de depuración en Release
#-assumenosideeffects class android.util.Log {
#    public static boolean isLoggable(java.lang.String, int);
#    public static int v(...);
#    public static int d(...);
#    public static int i(...);
#}
#
## Protege OkHttp y Okio contra ofuscación agresiva
#-dontwarn okhttp3.**
#-dontwarn okio.**
#-keepnames class okhttp3.**
#-keepnames class okio.**


# 1. Mantener intactos los modelos de dominio y estados del semáforo (:core)
-keep class es.jlrn.core.** { *; }

# 2. Proteger la serialización JSON nativa (respuestas de Gemini API)
-keep class org.json.** { *; }

# 3. Protege OkHttp y Okio contra ofuscación agresiva
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn javax.annotation.**
-keepnames class okhttp3.**
-keepnames class okio.**

# 4. Elimina logs de depuración en Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
