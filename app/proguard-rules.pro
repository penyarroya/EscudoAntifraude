# Elimina logs de depuración en Release
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Protege OkHttp y Okio contra ofuscación agresiva
-dontwarn okhttp3.**
-dontwarn okio.**
-keepnames class okhttp3.**
-keepnames class okio.**
