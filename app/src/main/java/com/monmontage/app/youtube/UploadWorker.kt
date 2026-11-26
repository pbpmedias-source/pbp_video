package com.monmontage.app.youtube

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.monmontage.app.data.ProjectRepository
import com.monmontage.app.model.YouTubeInfo

/**
 * Envoi en arrière-plan (WorkManager, service au premier plan avec notification) :
 * l'envoi continue si l'utilisateur quitte l'application et attend le Wi-Fi si demandé.
 */
class UploadWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        const val KEY_PROJECT = "project"
        const val KEY_URI = "uri"
        const val KEY_TITLE = "title"
        const val KEY_DESCRIPTION = "description"
        const val KEY_PRIVACY = "privacy"
        const val KEY_KIDS = "kids"
        const val KEY_SENT = "sent"
        const val KEY_TOTAL = "total"
        const val KEY_VIDEO_ID = "videoId"
        const val KEY_ERROR = "error"
        private const val CHANNEL = "uploads"
        private const val NOTIF_ID = 4242

        fun uniqueName(projectId: String) = "youtube-$projectId"

        fun enqueue(context: Context, projectId: String, uri: Uri, meta: UploadMetadata, wifiOnly: Boolean) {
            val request = OneTimeWorkRequestBuilder<UploadWorker>()
                .setInputData(workDataOf(
                    KEY_PROJECT to projectId, KEY_URI to uri.toString(), KEY_TITLE to meta.title,
                    KEY_DESCRIPTION to meta.description, KEY_PRIVACY to meta.privacy, KEY_KIDS to meta.madeForKids,
                ))
                .setConstraints(Constraints.Builder()
                    .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                    .build())
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(uniqueName(projectId), ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context, projectId: String) {
            WorkManager.getInstance(context).cancelUniqueWork(uniqueName(projectId))
            sessions(context).edit().remove(projectId).apply()
        }

        private fun sessions(context: Context) = context.getSharedPreferences("upload_sessions", Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result {
        val projectId = inputData.getString(KEY_PROJECT) ?: return Result.failure()
        val uri = Uri.parse(inputData.getString(KEY_URI) ?: return Result.failure())
        val meta = UploadMetadata(
            inputData.getString(KEY_TITLE).orEmpty(),
            inputData.getString(KEY_DESCRIPTION).orEmpty(),
            inputData.getString(KEY_PRIVACY) ?: "private",
            inputData.getBoolean(KEY_KIDS, false),
        )
        val ctx = applicationContext
        runCatching { setForeground(foregroundInfo(meta.title, 0)) }

        return try {
            val size = ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize }
                ?: return fail("La vidéo exportée est introuvable dans la galerie. Exportez à nouveau.")
            val token: suspend () -> String = {
                YouTubeAuth.silentToken(ctx) ?: throw UploadException("Reconnectez votre compte YouTube depuis l'application.")
            }
            val uploader = YouTubeUploader(ctx)
            val prefs = sessions(ctx)
            var session = prefs.getString(projectId, null)
            var offset = 0L
            if (session != null) {
                val (o, done) = runCatching { uploader.queryOffset(session, token(), size) }.getOrElse { 0L to null }
                if (done != null) return succeed(projectId, done, meta)
                offset = o
            }
            if (session == null) {
                session = uploader.startSession(token(), meta, size)
                prefs.edit().putString(projectId, session).apply()
            }
            var lastPercent = -1
            val videoId = uploader.upload(session, uri, size, offset, token) { sent ->
                val percent = (sent * 100 / size).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    setProgress(workDataOf(KEY_SENT to sent, KEY_TOTAL to size))
                    runCatching { setForeground(foregroundInfo(meta.title, percent)) }
                }
            }
            succeed(projectId, videoId, meta)
        } catch (e: UploadException) {
            if (e.retryable && runAttemptCount < 3) Result.retry() else fail(e.message ?: "Envoi impossible.")
        } catch (e: java.io.IOException) {
            if (runAttemptCount < 3) Result.retry() else fail("Connexion perdue. Réessayez quand le réseau est stable.")
        }
    }

    private fun succeed(projectId: String, videoId: String, meta: UploadMetadata): Result {
        sessions(applicationContext).edit().remove(projectId).apply()
        val repo = ProjectRepository(applicationContext)
        repo.load(projectId)?.let { repo.save(it.copy(youtube = YouTubeInfo(videoId, meta.privacy, System.currentTimeMillis()))) }
        return Result.success(workDataOf(KEY_VIDEO_ID to videoId))
    }

    private fun fail(message: String): Result = Result.failure(workDataOf(KEY_ERROR to message))

    private fun foregroundInfo(title: String, percent: Int): ForegroundInfo {
        val nm = applicationContext.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL) == null) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, "Envois YouTube", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setContentTitle("Envoi sur YouTube")
            .setContentText("« $title » · $percent %")
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29)
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        else ForegroundInfo(NOTIF_ID, notification)
    }
}
