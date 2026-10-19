package com.auristube.bridge

import android.app.Activity
import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Pantalla de primer arranque: si TizenTube Cobalt no esta instalado, descarga
 * el APK mas reciente desde GitHub y lanza el instalador de Android.
 */
class SetupActivity : Activity() {

    private lateinit var statusView: TextView
    private lateinit var actionButton: Button
    private lateinit var progressView: ProgressBar

    private val handler = Handler(Looper.getMainLooper())
    private var downloadId = -1L
    private var pendingInstallUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)

        statusView = findViewById(R.id.setup_status)
        actionButton = findViewById(R.id.setup_action)
        progressView = findViewById(R.id.setup_progress)

        actionButton.setOnClickListener { onAction() }
        refresh()
    }

    override fun onResume() {
        super.onResume()
        val uri = pendingInstallUri
        if (uri != null && canInstallPackages()) {
            pendingInstallUri = null
            installApk(uri)
        } else if (downloadId == -1L) {
            refresh()
        }
    }

    private fun refresh() {
        if (isTargetInstalled()) {
            statusView.setText(R.string.setup_installed)
            actionButton.setText(R.string.setup_open)
            actionButton.tag = ACTION_OPEN
        } else {
            statusView.setText(R.string.setup_missing)
            actionButton.setText(R.string.setup_download)
            actionButton.tag = ACTION_DOWNLOAD
        }
        actionButton.isEnabled = true
        progressView.visibility = View.GONE
    }

    private fun onAction() {
        when (actionButton.tag) {
            ACTION_OPEN -> openTarget()
            ACTION_DOWNLOAD -> resolveAndDownload()
        }
    }

    private fun isTargetInstalled(): Boolean =
        packageManager.getLeanbackLaunchIntentForPackage(TARGET_PACKAGE) != null ||
            packageManager.getLaunchIntentForPackage(TARGET_PACKAGE) != null

    private fun openTarget() {
        val intent = packageManager.getLeanbackLaunchIntentForPackage(TARGET_PACKAGE)
            ?: packageManager.getLaunchIntentForPackage(TARGET_PACKAGE)
        if (intent == null) {
            refresh()
            return
        }
        startActivity(intent)
    }

    private fun resolveAndDownload() {
        actionButton.isEnabled = false
        progressView.visibility = View.VISIBLE
        progressView.isIndeterminate = true
        statusView.setText(R.string.setup_searching)

        Thread {
            val url = try {
                resolveLatestApkUrl()
            } catch (t: Throwable) {
                null
            }
            handler.post {
                if (url == null) {
                    statusView.setText(R.string.setup_search_failed)
                    actionButton.isEnabled = true
                    progressView.visibility = View.GONE
                } else {
                    startDownload(url)
                }
            }
        }.start()
    }

    private fun resolveLatestApkUrl(): String? {
        val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 15000
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "AurisTube")
        }
        try {
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val assets = JSONObject(body).getJSONArray("assets")
            val urls = ArrayList<String>()
            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val url = asset.optString("browser_download_url", "")
                if (name.endsWith(".apk", true) && url.isNotEmpty()) {
                    urls.add(url)
                }
            }
            return urls.firstOrNull { it.contains("v7", true) || it.contains("arm", true) }
                ?: urls.firstOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun startDownload(url: String) {
        statusView.setText(R.string.setup_downloading)
        progressView.isIndeterminate = false
        progressView.progress = 0
        try {
            val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle("TizenTube Cobalt")
                .setMimeType("application/vnd.android.package-archive")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    this,
                    Environment.DIRECTORY_DOWNLOADS,
                    "tizentube-cobalt.apk"
                )
            downloadId = manager.enqueue(request)
            pollDownload()
        } catch (t: Throwable) {
            statusView.setText(R.string.setup_download_failed)
            actionButton.isEnabled = true
            progressView.visibility = View.GONE
        }
    }

    private fun pollDownload() {
        if (downloadId == -1L) {
            return
        }
        val manager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val cursor = manager.query(DownloadManager.Query().setFilterById(downloadId))
        var retry = true
        cursor?.use { c ->
            if (c.moveToFirst()) {
                val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                val done = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                if (total > 0) {
                    progressView.progress = ((done * 100) / total).toInt()
                }
                when (status) {
                    DownloadManager.STATUS_SUCCESSFUL -> {
                        retry = false
                        val uri = manager.getUriForDownloadedFile(downloadId)
                        downloadId = -1L
                        installApk(uri)
                    }
                    DownloadManager.STATUS_FAILED -> {
                        retry = false
                        downloadId = -1L
                        statusView.setText(R.string.setup_download_failed)
                        actionButton.isEnabled = true
                        progressView.visibility = View.GONE
                    }
                }
            }
        }
        if (retry) {
            handler.postDelayed({ pollDownload() }, 600)
        }
    }

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || packageManager.canRequestPackageInstalls()

    private fun installApk(uri: Uri?) {
        if (uri == null) {
            statusView.setText(R.string.setup_download_failed)
            actionButton.isEnabled = true
            progressView.visibility = View.GONE
            return
        }

        if (!canInstallPackages()) {
            pendingInstallUri = uri
            statusView.setText(R.string.setup_allow_install)
            actionButton.isEnabled = true
            progressView.visibility = View.GONE
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:$packageName")
                    )
                )
            } catch (ignored: ActivityNotFoundException) {
            }
            return
        }

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            startActivity(intent)
            statusView.setText(R.string.setup_installing)
        } catch (ignored: ActivityNotFoundException) {
            statusView.setText(R.string.setup_download_failed)
        }
        actionButton.isEnabled = true
        progressView.visibility = View.GONE
    }

    companion object {
        private const val TARGET_PACKAGE = "io.gh.reisxd.tizentube.cobalt"
        private const val RELEASES_API =
            "https://api.github.com/repos/reisxd/TizenTubeCobalt/releases/latest"
        private const val ACTION_OPEN = "open"
        private const val ACTION_DOWNLOAD = "download"
    }
}
