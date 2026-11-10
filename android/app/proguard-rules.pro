# Conserva los métodos que la página puede llamar desde JavaScript.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
