package app.tonalli

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewFeature
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import org.json.JSONObject

/**
 * Única pantalla nativa: un WebView que abre la app web guardada en assets/index.html.
 *
 * Qué hace la capa nativa:
 *  - Sirve los archivos locales con https://appassets.androidplatform.net (localStorage funciona).
 *  - Respeta las barras del sistema (pantalla completa de borde a borde).
 *  - Abre los enlaces externos en el navegador del teléfono.
 *  - Permite elegir fotos para las ofertas (selector de archivos).
 *  - Ofrece el escáner de códigos de barras de Google a la página (window.TonalliNative).
 *  - Conecta el botón Atrás del teléfono con la navegación interna de la página.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var fileCallback: ValueCallback<Array<Uri>>? = null

    private val filePicker =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            fileCallback?.onReceiveValue(
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            )
            fileCallback = null
        }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        webView = WebView(this)
        val root = FrameLayout(this)
        root.addView(
            webView,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        setContentView(root)

        // Deja espacio para la barra de estado, la de navegación, la muesca y el teclado.
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or
                    WindowInsetsCompat.Type.displayCutout() or
                    WindowInsetsCompat.Type.ime()
            )
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            WindowInsetsCompat.CONSUMED
        }

        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES
        WindowCompat.getInsetsController(window, root).apply {
            isAppearanceLightStatusBars = !night
            isAppearanceLightNavigationBars = !night
        }

        val assetLoader = WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(this))
            .build()

        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun shouldOverrideUrlLoading(
                view: WebView,
                request: WebResourceRequest
            ): Boolean {
                val url = request.url
                if (url.host == HOST) return false
                // Cualquier otra dirección se abre fuera de la app, y solo si es http o https.
                if (url.scheme == "https" || url.scheme == "http") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                    }
                }
                return true
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowFileChooser(
                view: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                try {
                    filePicker.launch(params.createIntent())
                } catch (_: Exception) {
                    fileCallback = null
                    callback.onReceiveValue(null)
                }
                return true
            }
        }

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
        }
        // La página trae su propio tema oscuro; no se permite que Android lo oscurezca otra vez.
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, false)
        }
        val debuggable = (applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        WebView.setWebContentsDebuggingEnabled(debuggable)

        webView.addJavascriptInterface(NativeBridge { startBarcodeScan() }, "TonalliNative")

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // La página responde true si ella manejó el "atrás"; si no, se cierra la app.
                webView.evaluateJavascript(
                    "window.nativeBack ? window.nativeBack() : false"
                ) { answer ->
                    if (answer != "true") {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            }
        })

        webView.loadUrl("https://$HOST/assets/index.html")
    }

    /** Abre el escáner de Google y devuelve el número leído a la página. */
    private fun startBarcodeScan() {
        val options = GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .build()
        GmsBarcodeScanning.getClient(this, options)
            .startScan()
            .addOnSuccessListener { barcode ->
                barcode.rawValue?.let { sendToPage("onNativeBarcode", it) }
            }
            .addOnCanceledListener { /* la persona cerró el escáner */ }
            .addOnFailureListener { error ->
                sendToPage("onNativeScanError", error.message ?: "")
            }
    }

    private fun sendToPage(function: String, argument: String) {
        webView.evaluateJavascript(
            "window.$function && window.$function(${JSONObject.quote(argument)})",
            null
        )
    }

    /** Lo único que la página puede pedirle a Android. Se usa como window.TonalliNative. */
    class NativeBridge(private val onScan: () -> Unit) {
        @JavascriptInterface
        fun scanBarcode() {
            // Llega en un hilo de fondo: se pasa al hilo principal.
            android.os.Handler(android.os.Looper.getMainLooper()).post { onScan() }
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        private const val HOST = "appassets.androidplatform.net"
    }
}
