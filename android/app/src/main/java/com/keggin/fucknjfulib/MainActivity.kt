package com.keggin.fucknjfulib
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.inputmethod.EditorInfo
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.keggin.fucknjfulib.databinding.ActivityMainBinding
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: SharedPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.topAppBar)
        supportActionBar?.title = getString(R.string.app_name)
        preferences = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        configureWebView()
        configureSwipeRefresh()
        setupBackPressHandler()
        if (savedInstanceState != null) {
            binding.webView.restoreState(savedInstanceState)
        } else {
            binding.webView.loadUrl(currentServerUrl())
        }
    }
    private fun configureSwipeRefresh() {
        binding.swipeRefresh.setOnRefreshListener {
            binding.webView.reload()
        }
        binding.swipeRefresh.setColorSchemeResources(
            android.R.color.holo_green_dark,
            android.R.color.holo_green_light,
            android.R.color.holo_green_dark
        )
    }
    private fun configureWebView() = with(binding.webView) {
        val webView = this
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(webView, true)
            }
        }
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            allowFileAccess = true
            allowContentAccess = true
        }
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val uri = request.url
                return when (uri.scheme?.lowercase()) {
                    "http", "https" -> false
                    else -> {
                        try {
                            startActivity(Intent(Intent.ACTION_VIEW, uri))
                        } catch (_: Exception) {
                            Toast.makeText(this@MainActivity, getString(R.string.message_loading_error), Toast.LENGTH_SHORT).show()
                        }
                        true
                    }
                }
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                binding.swipeRefresh.isRefreshing = false
                binding.progressBar.isVisible = false
            }
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: android.webkit.WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    Toast.makeText(
                        this@MainActivity,
                        getString(R.string.message_loading_error),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                binding.progressBar.isVisible = newProgress in 1..99
                binding.progressBar.progress = newProgress
                if (newProgress == 100) {
                    binding.progressBar.isVisible = false
                    binding.swipeRefresh.isRefreshing = false
                }
            }
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                binding.topAppBar.subtitle = title
            }
        }
    }
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
        R.id.action_reload -> {
            binding.webView.reload()
            true
        }
        R.id.action_change_server -> {
            promptForServerUrl()
            true
        }
        else -> super.onOptionsItemSelected(item)
    }
    override fun onSaveInstanceState(outState: Bundle) {
        binding.webView.saveState(outState)
        super.onSaveInstanceState(outState)
    }
    private fun setupBackPressHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.webView.canGoBack()) {
                    binding.webView.goBack()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }
    override fun onDestroy() {
        binding.webView.apply {
            loadUrl("about:blank")
            stopLoading()
            clearHistory()
            removeAllViews()
            destroy()
        }
        super.onDestroy()
    }
    private fun promptForServerUrl() {
        val current = currentServerUrl()
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            imeOptions = EditorInfo.IME_ACTION_DONE
            setText(current)
            setSelection(current.length)
            hint = getString(R.string.dialog_hint_server_url)
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_change_server)
            .setView(input)
            .setPositiveButton(R.string.dialog_action_save) { dialog, _ ->
                val newUrl = input.text.toString().trim()
                if (newUrl.isBlank() || !isValidUrl(newUrl)) {
                    Toast.makeText(this, R.string.message_invalid_url, Toast.LENGTH_SHORT).show()
                } else {
                    preferences.edit().putString(KEY_SERVER_URL, formatUrl(newUrl)).apply()
                    binding.webView.loadUrl(currentServerUrl())
                    Toast.makeText(
                        this,
                        getString(R.string.message_updating_url, currentServerUrl()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_action_cancel) { dialog, _ -> dialog.dismiss() }
            .show()
    }
    private fun currentServerUrl(): String {
        return preferences.getString(KEY_SERVER_URL, BuildConfig.DEFAULT_SERVER_URL) ?: BuildConfig.DEFAULT_SERVER_URL
    }
    private fun isValidUrl(value: String): Boolean {
        val formatted = formatUrl(value)
        val uri = Uri.parse(formatted)
        return uri != null && (uri.scheme == "http" || uri.scheme == "https") && !uri.host.isNullOrBlank()
    }
    private fun formatUrl(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }
    companion object {
        private const val PREFS_NAME = "fucknjfulib"
        private const val KEY_SERVER_URL = "server_url"
    }
}