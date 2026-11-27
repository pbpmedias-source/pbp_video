package com.monmontage.app.render

import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import com.google.common.collect.ImmutableList
import com.monmontage.app.model.ClipType
import com.monmontage.app.model.TransitionStyle
import java.io.File
import kotlin.math.min

/**
 * Traduit la timeline en Composition Media3 :
 *  - séquence 1 (vidéo + son) : générique, photos, segments vidéo, générique de fin ;
 *  - séquence 2 (audio seul)  : la musique, répétée pour couvrir toute la durée,
 *    avec un processeur de gain qui applique l'enveloppe (fondus + coupure pendant les interviews).
 * Transformer mixe les deux séquences, puis encode en H.264 + AAC.
 */
@UnstableApi
object CompositionFactory {
    private const val IMAGE_FPS = 30

    fun build(prepared: PreparedProject, spec: OutputSpec): Composition {
        val timeline = prepared.timeline
        // Chaque élément est mis à l'échelle (bandes noires si besoin) dans le cadre 16:9 de sortie.
        val presentation = Presentation.createForWidthAndHeight(spec.width, spec.height, Presentation.LAYOUT_SCALE_TO_FIT)

        val count = timeline.items.size
        val videoItems = timeline.items.mapIndexed { index, item ->
            val transition = TransitionEffects.videoEffects(prepared.transition, index, count, item.durationMs)
            val isZoom = prepared.transition.style == TransitionStyle.ZOOM
            // Ordre : mise à l'échelle → zoom d'entrée → bandeau → logo en miniature → fondu.
            fun effects(captions: List<LocalCaption>, withLogo: Boolean = false): List<Effect> = buildList {
                add(presentation)
                if (isZoom) addAll(transition)
                addAll(captionEffects(captions, spec, prepared.colors))
                val wm = prepared.watermarkFile
                if (withLogo && wm != null) add(OverlayEffect(ImmutableList.of<TextureOverlay>(WatermarkOverlay(wm))))
                if (!isZoom) addAll(transition)
            }
            when (item) {
                is TimelineItem.TitleCardItem ->
                    imageItem(requireNotNull(prepared.titleCardFile), MimeTypes.IMAGE_PNG, item.durationMs, effects(emptyList()))
                is TimelineItem.EndCardItem ->
                    imageItem(requireNotNull(prepared.endCardFile), MimeTypes.IMAGE_PNG, item.durationMs, effects(emptyList()))
                is TimelineItem.CartonItem ->
                    imageItem(prepared.cartonFiles.getValue(item.clip.id), MimeTypes.IMAGE_PNG, item.durationMs, effects(emptyList()))
                is TimelineItem.PhotoItem ->
                    imageItem(prepared.photoFiles.getValue(item.clip.id), MimeTypes.IMAGE_JPEG, item.durationMs, effects(item.captions, withLogo = item.clip.showLogo))
                is TimelineItem.VideoItem -> {
                    val gain = TransitionEffects.audioGain(prepared.transition, index, count, item.durationMs)
                    videoItem(item, effects(item.captions, withLogo = item.clip.showLogo), gain)
                }
            }
        }

        val sequences = mutableListOf(EditedMediaItemSequence(videoItems))
        val music = musicItems(prepared)
        if (music.isNotEmpty()) sequences += EditedMediaItemSequence(music)

        return Composition.Builder(sequences)
            .experimentalSetForceAudioTrack(true)   // silence généré pour les photos/génériques
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL) // vidéos HDR -> SDR compatible H.264
            .build()
    }

    private fun imageItem(file: File, mime: String, durationMs: Long, effects: List<Effect>): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.fromFile(file))
            .setMimeType(mime)
            .setImageDurationMs(durationMs)
            .build()
        @Suppress("DEPRECATION")
        return EditedMediaItem.Builder(mediaItem)
            .setDurationUs(durationMs * 1000)
            .setFrameRate(IMAGE_FPS)
            .setEffects(Effects(emptyList(), effects))
            .build()
    }

    private fun videoItem(item: TimelineItem.VideoItem, video: List<Effect>, gain: (Long) -> Float): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(item.clip.uri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(item.segment.startMs)
                    .setEndPositionMs(item.segment.endMs)
                    .build()
            )
            .build()
        // Hypothèse : le son d'origine des vidéos d'illustration est coupé (la musique les accompagne),
        // celui des interviews est conservé, avec des fondus aux bords selon la transition.
        val audio: List<AudioProcessor> =
            if (item.clip.type == ClipType.ILLUSTRATION) listOf(GainEnvelopeProcessor(0) { 0f })
            else listOf(GainEnvelopeProcessor(0, gain))
        return EditedMediaItem.Builder(mediaItem).setEffects(Effects(audio, video)).build()
    }

    private fun captionEffects(captions: List<LocalCaption>, spec: OutputSpec, colors: KitColors): List<Effect> {
        if (captions.isEmpty()) return emptyList()
        val overlays: List<TextureOverlay> = captions.map { CaptionOverlay(it, spec.width, spec.height, colors) }
        return listOf(OverlayEffect(ImmutableList.copyOf(overlays)))
    }

    private fun musicItems(prepared: PreparedProject): List<EditedMediaItem> {
        val total = prepared.timeline.totalMs
        if (prepared.music.volume <= 0f || total <= 0) return emptyList()
        val envelope = MusicEnvelope(
            interviewRangesMs = prepared.timeline.interviewRanges,
            totalMs = total,
            volume = prepared.music.volume,
            duckLevel = if (prepared.music.muteDuringInterviews) 0f else 0.12f,
        )
        val items = mutableListOf<EditedMediaItem>()
        var offset = 0L
        while (total - offset >= 200) {
            val len = min(prepared.musicDurationMs, total - offset)
            val mediaItem = MediaItem.Builder()
                .setUri(prepared.musicUri)
                .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setEndPositionMs(len).build())
                .build()
            val processor = GainEnvelopeProcessor(offset * 1000, envelope::gainAtUs)
            items += EditedMediaItem.Builder(mediaItem)
                .setRemoveVideo(true)
                .setEffects(Effects(listOf<AudioProcessor>(processor), emptyList()))
                .build()
            offset += len
        }
        return items
    }
}
