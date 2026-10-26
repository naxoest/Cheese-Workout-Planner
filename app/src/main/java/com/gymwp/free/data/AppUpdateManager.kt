package com.gymwp.free.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import com.google.gson.Gson
import com.gymwp.free.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteVersionInfo(
    val versionCode: Int = 201,
    val versionName: String = "2.1.0",
    val apkUrl: String = "https://raw.githubusercontent.com/naxoest/GymWP-Free/main/v1.apk",
    val releaseNotes: String = ""
)

sealed class AppUpdateState {
    data object Idle : AppUpdateState()
    data object Checking : AppUpdateState()
    data class UpToDate(val currentVersionName: String, val currentVersionCode: Int) : AppUpdateState()
    data class UpdateAvailable(val remote: RemoteVersionInfo, val currentVersionName: String) : AppUpdateState()
    data class Downloading(val remote: RemoteVersionInfo, val progressPercent: Int) : AppUpdateState()
    data class ReadyToInstall(val apkFile: File, val remote: RemoteVersionInfo) : AppUpdateState()
    data class Error(val message: String) : AppUpdateState()
}

object AppUpdateManager {

    private const val VERSION_JSON_URL = "https://raw.githubusercontent.com/naxoest/GymWP-Free/main/version.json"
    private const val CHANNEL_ID = "chukaros_app_updates"
    private const val NOTIFICATION_ID = 9001

    fun getInstalledVersion(context: Context): Pair<Int, String> {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                pInfo.versionCode
            }
            code to (pInfo.versionName ?: "2.1.0")
        } catch (_: PackageManager.NameNotFoundException) {
            201 to "2.1.0"
        }
    }

    suspend fun checkForUpdates(context: Context, notifyIfAvailable: Boolean = true): AppUpdateState =
        withContext(Dispatchers.IO) {
            val (installedCode, installedName) = getInstalledVersion(context)
            try {
                val url = URL("$VERSION_JSON_URL?t=${System.currentTimeMillis()}")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8000
                    readTimeout = 8000
                    requestMethod = "GET"
                    useCaches = false
                }
                if (conn.responseCode != 200) {
                    return@withContext AppUpdateState.UpToDate(installedName, installedCode)
                }
                val jsonText = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                val remote = Gson().fromJson(jsonText, RemoteVersionInfo::class.java)
                    ?: return@withContext AppUpdateState.UpToDate(installedName, installedCode)

                if (remote.versionCode > installedCode) {
                    if (notifyIfAvailable) {
                        postUpdateNotification(context, remote)
                    }
                    AppUpdateState.UpdateAvailable(remote, installedName)
                } else {
                    AppUpdateState.UpToDate(installedName, installedCode)
                }
            } catch (e: Exception) {
                AppUpdateState.Error("Sin conexión para verificar actualizaciones (${e.localizedMessage ?: "offline"})")
            }
        }

    private fun postUpdateNotification(context: Context, remote: RemoteVersionInfo) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "Actualizaciones de Chukaros's GymPlanner",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Notifica cuando hay una nueva versión disponible en GitHub"
                }
                nm.createNotificationChannel(channel)
            }
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("¡Nueva versión disponible (v${remote.versionName})!")
                .setContentText(remote.releaseNotes.ifBlank { "Toca para actualizar Chukaros's GymPlanner sin perder tus datos." })
                .setStyle(NotificationCompat.BigTextStyle().bigText(remote.releaseNotes))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()
            nm.notify(NOTIFICATION_ID, notification)
        } catch (_: Exception) {
        }
    }

    suspend fun downloadAndTriggerInstall(
        context: Context,
        remote: RemoteVersionInfo,
        onProgress: (Int) -> Unit
    ): AppUpdateState = withContext(Dispatchers.IO) {
        try {
            val url = URL("${remote.apkUrl}?t=${System.currentTimeMillis()}")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 30000
                instanceFollowRedirects = true
            }
            conn.connect()
            if (conn.responseCode !in 200..299) {
                return@withContext AppUpdateState.Error("Error al descargar APK (HTTP ${conn.responseCode})")
            }
            val totalBytes = conn.contentLength.coerceAtLeast(1)
            val outFile = File(context.cacheDir, "Chukaros_GymPlanner_Update.apk")
            if (outFile.exists()) outFile.delete()

            conn.inputStream.use { input ->
                FileOutputStream(outFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var downloaded = 0L
                    var read: Int
                    var lastReportedPct = -1
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val pct = ((downloaded * 100L) / totalBytes).toInt().coerceIn(0, 100)
                        if (pct != lastReportedPct) {
                            lastReportedPct = pct
                            onProgress(pct)
                        }
                    }
                    output.flush()
                }
            }
            withContext(Dispatchers.Main) {
                launchApkInstaller(context, outFile)
            }
            AppUpdateState.ReadyToInstall(outFile, remote)
        } catch (e: Exception) {
            AppUpdateState.Error("No se pudo descargar la actualización: ${e.localizedMessage ?: "Error de red"}")
        }
    }

    fun launchApkInstaller(context: Context, apkFile: File) {
        try {
            val apkUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                apkFile
            )
            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(installIntent)
        } catch (_: Exception) {
        }
    }
}
