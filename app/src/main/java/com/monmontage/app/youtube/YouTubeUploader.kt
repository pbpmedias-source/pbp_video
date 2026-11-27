package com.monmontage.app.youtube

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class UploadMetadata(
    val title: String,
    val description: String,
    /** "private", "unlisted" ou "public" */
    val privacy: String,
    val madeForKids: Boolean,
    /** Publication programmée (ISO 8601 UTC, ex. 2026-12-01T17:00:00Z) ; la vidéo reste privée jusque-là. */
    val publishAt: String? = null,
    /** Langue de la vidéo et de la parole : améliore les sous-titres automatiques de YouTube. */
    val language: String = "fr",
)

class UploadException(message: String, val retryable: Boolean = false) : Exception(message)

/**
 * Envoi « reprenable » (resumable upload) de l'API YouTube Data v3 :
 * une session est ouverte, puis le fichier part par blocs de 8 Mo ; après une coupure,
 * on demande au serveur où il en est et on repart de là. Le fichier est lu en flux.
 */
class YouTubeUploader(private val context: Context) {

    companion object {
        private const val CHUNK = 8 * 1024 * 1024   // multiple de 256 Ko, exigé par l'API
        private const val ENDPOINT =
            "https://www.googleapis.com/upload/youtube/v3/videos?uploadType=resumable&part=snippet,status"
    }

    /** Ouvre la session d'envoi et renvoie son adresse. */
    fun startSession(token: String, meta: UploadMetadata, size: Long): String {
        val body = JSONObject().apply {
            put("snippet", JSONObject().apply {
                put("title", meta.title.take(100).ifBlank { "Montage" })
                put("description", meta.description.take(5000))
                put("categoryId", "22")
                put("defaultLanguage", meta.language)
                put("defaultAudioLanguage", meta.language)
            })
            put("status", JSONObject().apply {
                put("privacyStatus", if (meta.publishAt != null) "private" else meta.privacy)
                meta.publishAt?.let { put("publishAt", it) }
                put("selfDeclaredMadeForKids", meta.madeForKids)
            })
        }.toString().toByteArray()
        val c = open(ENDPOINT, "POST", token).apply {
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("X-Upload-Content-Type", "video/mp4")
            setRequestProperty("X-Upload-Content-Length", size.toString())
            doOutput = true
            setFixedLengthStreamingMode(body.size)
        }
        try {
            c.outputStream.use { it.write(body) }
            val code = c.responseCode
            if (code != 200) throw httpError(c, code)
            return c.getHeaderField("Location") ?: throw UploadException("Session d'envoi refusée par YouTube.")
        } finally { c.disconnect() }
    }

    /** Position déjà reçue par YouTube, ou null si la vidéo est complète (alors [completedId] est rempli). */
    fun queryOffset(session: String, token: String, size: Long): Pair<Long, String?> {
        val c = open(session, "PUT", token).apply {
            setRequestProperty("Content-Range", "bytes */$size")
            doOutput = true
            setFixedLengthStreamingMode(0)
        }
        try {
            c.outputStream.close()
            return when (val code = c.responseCode) {
                308 -> nextOffset(c) to null
                200, 201 -> size to videoId(c)
                else -> throw httpError(c, code)
            }
        } finally { c.disconnect() }
    }

    /** Envoie le fichier à partir de [startOffset] ; renvoie l'identifiant de la vidéo. */
    suspend fun upload(
        session: String,
        uri: Uri,
        size: Long,
        startOffset: Long,
        token: suspend () -> String,
        onProgress: suspend (Long) -> Unit,
    ): String {
        var offset = startOffset
        var attempts = 0
        val buffer = ByteArray(CHUNK)
        while (true) {
            try {
                val len = readChunk(uri, offset, buffer, size)
                val c = open(session, "PUT", token()).apply {
                    setRequestProperty("Content-Type", "video/mp4")
                    setRequestProperty("Content-Range", "bytes $offset-${offset + len - 1}/$size")
                    doOutput = true
                    setFixedLengthStreamingMode(len)
                }
                try {
                    c.outputStream.use { out ->
                        var written = 0
                        while (written < len) {
                            val n = minOf(64 * 1024, len - written)
                            out.write(buffer, written, n)
                            written += n
                            onProgress(offset + written)
                        }
                    }
                    when (val code = c.responseCode) {
                        308 -> { offset = nextOffset(c); attempts = 0; onProgress(offset) }
                        200, 201 -> return videoId(c) ?: throw UploadException("Réponse inattendue de YouTube.")
                        else -> throw httpError(c, code)
                    }
                } finally { c.disconnect() }
            } catch (e: Exception) {
                val retryable = e is IOException || (e is UploadException && e.retryable)
                if (!retryable || ++attempts > 8) throw e
                delay(minOf(60_000L, 1000L shl attempts))         // 2 s, 4 s, 8 s… jusqu'à 1 min
                val (o, id) = runCatching { queryOffset(session, token(), size) }.getOrElse { offset to null }
                if (id != null) return id
                offset = o
            }
        }
    }

    private fun readChunk(uri: Uri, offset: Long, buffer: ByteArray, size: Long): Int {
        val want = minOf(buffer.size.toLong(), size - offset).toInt()
        val input = context.contentResolver.openInputStream(uri)
            ?: throw UploadException("La vidéo exportée est introuvable dans la galerie.")
        input.use { s ->
            var skipped = 0L
            while (skipped < offset) {
                val k = s.skip(offset - skipped)
                if (k <= 0) throw IOException("Lecture du fichier impossible.")
                skipped += k
            }
            var read = 0
            while (read < want) {
                val k = s.read(buffer, read, want - read)
                if (k < 0) break
                read += k
            }
            if (read != want) throw UploadException("Le fichier vidéo a changé pendant l'envoi.")
            return read
        }
    }

    private fun open(url: String, method: String, token: String) =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            instanceFollowRedirects = false            // 308 = « reprendre », pas une redirection
            connectTimeout = 30_000
            readTimeout = 120_000
            setRequestProperty("Authorization", "Bearer $token")
        }

    private fun nextOffset(c: HttpURLConnection): Long =
        c.getHeaderField("Range")?.substringAfter('-')?.toLongOrNull()?.plus(1) ?: 0L

    private fun videoId(c: HttpURLConnection): String? =
        runCatching { JSONObject(c.inputStream.bufferedReader().readText()).optString("id").ifBlank { null } }.getOrNull()

    private fun httpError(c: HttpURLConnection, code: Int): UploadException {
        val reason = runCatching {
            JSONObject(c.errorStream.bufferedReader().readText()).getJSONObject("error")
                .getJSONArray("errors").getJSONObject(0).optString("reason")
        }.getOrDefault("")
        val message = when {
            code == 401 -> "Session Google expirée."
            reason == "quotaExceeded" || reason == "uploadLimitExceeded" ->
                "Limite d'envois atteinte pour aujourd'hui. Réessayez demain."
            reason == "youtubeSignupRequired" -> "Ce compte Google n'a pas encore de chaîne YouTube. Créez-la depuis l'application YouTube."
            reason == "forbiddenPrivacySetting" -> "Cette visibilité n'est pas autorisée pour ce compte."
            reason == "invalidTitle" -> "Le titre n'est pas accepté par YouTube."
            reason == "invalidPublishAt" -> "La date de publication programmée n'est pas valide (elle doit être dans le futur)."
            code == 403 -> "YouTube refuse l'envoi (${reason.ifBlank { "accès refusé" }}). Vérifiez que la YouTube Data API est activée."
            code >= 500 -> "YouTube est momentanément indisponible."
            else -> "Erreur YouTube $code ${reason}".trim()
        }
        return UploadException(message, retryable = code >= 500 || code == 401)
    }
}
