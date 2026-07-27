package `in`.gov.nhmmp.ishaqti

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var noInternetLayout: LinearLayout
    private lateinit var permissionGateLayout: LinearLayout
    private lateinit var permissionStatusText: TextView

    private val homeUrl = "https://ishaqti.nhmmp.gov.in/"
    private val homeHost = Uri.parse(homeUrl).host

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingGeoOrigin: String? = null
    private var pendingGeoCallback: GeolocationPermissions.Callback? = null

    // These two permissions are mandatory before the app is usable at all.
    private val requiredPermissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    )

    private val gatePermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        evaluateGate()
    }

    private val geoPermissionLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        pendingGeoOrigin?.let { origin ->
            pendingGeoCallback?.invoke(origin, hasLocationPermission(), false)
            pendingGeoOrigin = null
            pendingGeoCallback = null
        }
    }

    private val fileChooserLauncher = registerForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        val results: Array<Uri>? = if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            val clipData = data.clipData
            if (clipData != null) {
                Array(clipData.itemCount) { i -> clipData.getItemAt(i).uri }
            } else {
                data.data?.let { arrayOf(it) }
            }
        } else null
        filePathCallback?.onReceiveValue(results)
        filePathCallback = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        noInternetLayout = findViewById(R.id.noInternetLayout)
        permissionGateLayout = findViewById(R.id.permissionGateLayout)
        permissionStatusText = findViewById(R.id.permissionStatusText)

        findViewById<Button>(R.id.retryButton).setOnClickListener { checkConnectionAndLoad() }
        findViewById<Button>(R.id.grantPermissionButton).setOnClickListener { onGrantButtonClicked() }

        setupWebView()

        onBackPressedDispatcher.addCallback(this) {
            if (permissionGateLayout.visibility == android.view.View.VISIBLE) {
                finish()
            } else if (webView.canGoBack()) {
                webView.goBack()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Covers the case where the user granted permission from Settings and came back.
        evaluateGate()
    }

    /** Mandatory gate: app is unusable until Camera + Location are granted. */
    private fun evaluateGate() {
        if (allRequiredPermissionsGranted()) {
            permissionGateLayout.visibility = android.view.View.GONE
            checkConnectionAndLoad()
        } else {
            permissionGateLayout.visibility = android.view.View.VISIBLE
            webView.visibility = android.view.View.GONE
            noInternetLayout.visibility = android.view.View.GONE
            progressBar.visibility = android.view.View.GONE
        }
    }

    private fun onGrantButtonClicked() {
        val prefs = getSharedPreferences("ishaqti_prefs", Context.MODE_PRIVATE)
        val askedBefore = prefs.getBoolean("asked_permissions_before", false)

        val permanentlyDenied = askedBefore && requiredPermissions.any {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED &&
                !shouldShowRequestPermissionRationale(it)
        }

        if (permanentlyDenied) {
            permissionStatusText.text = getString(R.string.permission_status_settings)
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            prefs.edit().putBoolean("asked_permissions_before", true).apply()
            permissionStatusText.text = getString(R.string.permission_status_denied)
            gatePermissionLauncher.launch(requiredPermissions)
        }
    }

    private fun allRequiredPermissionsGranted(): Boolean =
        hasCameraPermission() && hasLocationPermission()

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setGeolocationEnabled(true)
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW

        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url
                return if (url.host == homeHost) {
                    false
                } else {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, url))
                    } catch (_: Exception) {
                    }
                    true
                }
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                swipeRefresh.isRefreshing = false
                progressBar.visibility = android.view.View.GONE
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                error: WebResourceError
            ) {
                super.onReceivedError(view, request, error)
                if (request.isForMainFrame) {
                    showNoInternet()
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                progressBar.visibility = if (newProgress in 1..99) android.view.View.VISIBLE else android.view.View.GONE
                progressBar.progress = newProgress
            }

            // Native permission is already guaranteed granted by the gate before the WebView
            // ever loads, so this resolves instantly with no extra prompt shown to the user.
            override fun onGeolocationPermissionsShowPrompt(
                origin: String,
                callback: GeolocationPermissions.Callback
            ) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, false)
                } else {
                    pendingGeoOrigin = origin
                    pendingGeoCallback = callback
                    geoPermissionLauncher.launch(requiredPermissions)
                }
            }

            override fun onShowFileChooser(
                webView: WebView,
                callback: ValueCallback<Array<Uri>>,
                params: FileChooserParams
            ): Boolean {
                filePathCallback = callback
                try {
                    val intent = params.createIntent()
                    fileChooserLauncher.launch(intent)
                } catch (_: Exception) {
                    filePathCallback = null
                    return false
                }
                return true
            }
        }

        webView.setDownloadListener { url, _, contentDisposition, mimeType, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url))
                request.setMimeType(mimeType)
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
                request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                dm.enqueue(request)
            } catch (_: Exception) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            }
        }

        swipeRefresh.setOnRefreshListener { checkConnectionAndLoad() }
    }

    private fun checkConnectionAndLoad() {
        if (!allRequiredPermissionsGranted()) {
            evaluateGate()
            return
        }
        if (isOnline()) {
            noInternetLayout.visibility = android.view.View.GONE
            webView.visibility = android.view.View.VISIBLE
            if (webView.url == null) {
                webView.loadUrl(homeUrl)
            } else {
                webView.reload()
            }
        } else {
            swipeRefresh.isRefreshing = false
            showNoInternet()
        }
    }

    private fun showNoInternet() {
        noInternetLayout.visibility = android.view.View.VISIBLE
        webView.visibility = android.view.View.GONE
        progressBar.visibility = android.view.View.GONE
    }

    private fun isOnline(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val capabilities = cm.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}
