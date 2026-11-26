package com.monmontage.app.render

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Enveloppe Media3 Transformer dans une fonction suspendue annulable, avec suivi de progression. */
@UnstableApi
class VideoRenderer(context: Context) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())

    suspend fun render(
        composition: Composition,
        output: File,
        videoBitrate: Int,
        onProgress: (Int?) -> Unit,
    ) = withContext(Dispatchers.Main) {   // Transformer doit être piloté depuis un thread avec Looper
        coroutineScope {
            var transformer: Transformer? = null
            val holder = ProgressHolder()
            val poller = launch {
                while (isActive) {
                    transformer?.let { t ->
                        val state = t.getProgress(holder)
                        onProgress(if (state == Transformer.PROGRESS_STATE_AVAILABLE) holder.progress else null)
                    }
                    delay(400)
                }
            }
            try {
                suspendCancellableCoroutine<Unit> { cont ->
                    val encoderFactory = DefaultEncoderFactory.Builder(appContext)
                        .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(videoBitrate).build())
                        .setEnableFallback(true)
                        .build()
                    val t = Transformer.Builder(appContext)
                        .setVideoMimeType(MimeTypes.VIDEO_H264)   // exigence YouTube : H.264
                        .setAudioMimeType(MimeTypes.AUDIO_AAC)    // exigence YouTube : AAC
                        .setEncoderFactory(encoderFactory)
                        .addListener(object : Transformer.Listener {
                            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                                if (cont.isActive) cont.resume(Unit)
                            }

                            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                                if (cont.isActive) cont.resumeWithException(exportException)
                            }
                        })
                        .build()
                    transformer = t
                    cont.invokeOnCancellation { mainHandler.post { t.cancel() } }
                    output.parentFile?.mkdirs()
                    output.delete()
                    t.start(composition, output.absolutePath)
                }
            } finally {
                poller.cancel()
            }
        }
    }
}
