package com.example.canvas

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID
import kotlin.math.abs

/** Basic text animation presets. */
enum class TextAnimation {
    NONE, FADE_IN, POP, SLIDE_UP, SLIDE_DOWN
}

/** Transition style between adjacent video clips. */
enum class TransitionType {
    NONE, CROSSFADE, SLIDE_LEFT, SLIDE_RIGHT, FADE_BLACK
}

/** LUT-style color grade filter presets. */
enum class ColorFilterPreset {
    NONE, VIVID, VINTAGE, B_AND_W, CINEMATIC_TEAL_ORANGE, WARM, COOL, SEPIA
}

/** Normalized crop rect for video clip (0f..1f). */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val aspectPreset: String = "Free"
)

/** Transform keyframe for animating zoom, position, rotation, and opacity over time. */
data class TransformKeyframe(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long = 0L, // time relative to clip start
    val scale: Float = 1.0f,
    val translateXFraction: Float = 0f,
    val translateYFraction: Float = 0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f
)

/** Video clip in sequence with keyframe animation, color filters, speed, transitions, trim, crop, scale, rotation, mirror. */
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val cropRect: CropRect = CropRect(),
    val scale: Float = 1.0f,
    val translateXFraction: Float = 0f,
    val translateYFraction: Float = 0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f,
    val isMirrored: Boolean = false,
    val speed: Float = 1.0f,
    val transitionToNext: TransitionType = TransitionType.NONE,
    val transitionDurationMs: Long = 500L,
    val filterPreset: ColorFilterPreset = ColorFilterPreset.NONE,
    val keyframes: List<TransformKeyframe> = emptyList(),
    val nativeWidthPx: Int = 0,
    val nativeHeightPx: Int = 0
) {
    val trimmedDurationMs: Long
        get() {
            val rawMs = (trimEndMs - trimStartMs).coerceAtLeast(0L)
            val s = if (speed > 0.05f) speed else 1.0f
            return (rawMs / s).toLong().coerceAtLeast(0L)
        }

    /** Returns interpolated transform values at a given time inside the clip. */
    fun interpolatedTransformAt(timeInClipMs: Long): TransformKeyframe {
        if (keyframes.isEmpty()) {
            return TransformKeyframe(
                timeMs = timeInClipMs,
                scale = scale,
                translateXFraction = translateXFraction,
                translateYFraction = translateYFraction,
                rotationDegrees = rotationDegrees,
                opacity = opacity
            )
        }

        val sorted = keyframes.sortedBy { it.timeMs }
        if (timeInClipMs <= sorted.first().timeMs) return sorted.first()
        if (timeInClipMs >= sorted.last().timeMs) return sorted.last()

        for (i in 0 until sorted.size - 1) {
            val k1 = sorted[i]
            val k2 = sorted[i + 1]
            if (timeInClipMs in k1.timeMs..k2.timeMs) {
                val duration = (k2.timeMs - k1.timeMs).toFloat().coerceAtLeast(1f)
                val frac = ((timeInClipMs - k1.timeMs) / duration).coerceIn(0f, 1f)

                fun lerp(a: Float, b: Float) = a + (b - a) * frac

                return TransformKeyframe(
                    timeMs = timeInClipMs,
                    scale = lerp(k1.scale, k2.scale),
                    translateXFraction = lerp(k1.translateXFraction, k2.translateXFraction),
                    translateYFraction = lerp(k1.translateYFraction, k2.translateYFraction),
                    rotationDegrees = lerp(k1.rotationDegrees, k2.rotationDegrees),
                    opacity = lerp(k1.opacity, k2.opacity)
                )
            }
        }
        return sorted.last()
    }
}

/** Audio track layer with volume, mute, waveform data, and beat markers. */
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val startTimeMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val waveformData: List<Float> = emptyList(),
    val beatMarkersMs: List<Long> = emptyList()
) {
    val trimmedDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(0L)
}

/** Text overlay item with transform, style, opacity, and animation properties. */
data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val fontSizeSp: Float = 28f,
    val scale: Float = 1f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1f,
    val color: Color = Color.White,
    val backgroundColor: Color = Color.Transparent,
    val hasShadow: Boolean = true,
    val animation: TextAnimation = TextAnimation.NONE,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 10000L
)

/** Image/PNG overlay layer item with position, scale, rotation, opacity, and timeline placement. */
data class ImageOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val uri: Uri,
    val name: String = "Overlay",
    val xFraction: Float = 0.5f,
    val yFraction: Float = 0.5f,
    val scale: Float = 1.0f,
    val rotationDegrees: Float = 0f,
    val opacity: Float = 1.0f,
    val startTimeMs: Long = 0L,
    val durationMs: Long = 10000L
)

/** Export and canvas preset settings (Default: 9:16 for short-form). */
data class ExportSettings(
    val resolution: String = "1080p",
    val fps: Int = 30,
    val aspectRatio: String = "9:16"
)

/** Single, unified, immutable project state model. */
data class CanvasProject(
    val videoClips: List<VideoClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textLayers: List<TextOverlayItem> = emptyList(),
    val imageOverlays: List<ImageOverlayItem> = emptyList(),
    val muteOriginalAudio: Boolean = false,
    val selectedLayerId: String? = null,
    val playheadPositionMs: Long = 0L,
    val canvasSettings: ExportSettings = ExportSettings()
) {
    val totalDurationMs: Long
        get() = videoClips.sumOf { it.trimmedDurationMs }.coerceAtLeast(0L)

    val selectedVideoClip: VideoClip?
        get() = videoClips.find { it.id == selectedLayerId } ?: videoClips.firstOrNull()

    val selectedAudioClip: AudioClip?
        get() = audioClips.find { it.id == selectedLayerId } ?: audioClips.firstOrNull()

    val selectedTextLayer: TextOverlayItem?
        get() = textLayers.find { it.id == selectedLayerId } ?: textLayers.firstOrNull()

    val selectedImageOverlay: ImageOverlayItem?
        get() = imageOverlays.find { it.id == selectedLayerId } ?: imageOverlays.firstOrNull()
}

/** Saved project draft container for persistent internal storage. */
data class SavedProjectDraft(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Untitled Project",
    val lastModifiedMs: Long = System.currentTimeMillis(),
    val project: CanvasProject = CanvasProject()
)

// ============================================================
// VIDEO THUMBNAIL FILMSTRIP PROVIDER
// ============================================================

object ThumbnailProvider {
    private val memoryCache = mutableMapOf<String, List<Bitmap>>()

    fun getOrFetchThumbnails(
        context: Context,
        clip: VideoClip,
        count: Int = 5,
        onThumbnailsLoaded: (List<Bitmap>) -> Unit
    ) {
        val cacheKey = "${clip.id}_${clip.trimStartMs}_${clip.trimEndMs}_$count"
        val cached = memoryCache[cacheKey]
        if (cached != null) {
            onThumbnailsLoaded(cached)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val retriever = MediaMetadataRetriever()
            val frames = mutableListOf<Bitmap>()
            try {
                retriever.setDataSource(context, clip.uri)
                val durationUs = (clip.trimEndMs - clip.trimStartMs) * 1000L
                val stepUs = if (count > 1) durationUs / (count - 1) else durationUs

                for (i in 0 until count) {
                    val targetUs = (clip.trimStartMs * 1000L) + (i * stepUs)
                    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        retriever.getScaledFrameAtTime(
                            targetUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                            120,
                            90
                        )
                    } else {
                        retriever.getFrameAtTime(
                            targetUs,
                            MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                        )
                    }
                    if (bitmap != null) {
                        frames.add(bitmap)
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { retriever.release() } catch (_: Exception) {}
            }

            if (frames.isNotEmpty()) {
                memoryCache[cacheKey] = frames
                withContext(Dispatchers.Main) {
                    onThumbnailsLoaded(frames)
                }
            }
        }
    }
}

// ============================================================
// REAL AUDIO PCM WAVEFORM EXTRACTOR
// ============================================================

object AudioWaveformExtractor {
    private val waveformCache = mutableMapOf<String, List<Float>>()

    fun extractWaveform(
        context: Context,
        audio: AudioClip,
        sampleCount: Int = 40,
        onWaveformExtracted: (List<Float>) -> Unit
    ) {
        val cacheKey = "${audio.id}_${audio.trimStartMs}_${audio.trimEndMs}_$sampleCount"
        val cached = waveformCache[cacheKey]
        if (cached != null) {
            onWaveformExtracted(cached)
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val peaks = mutableListOf<Float>()
            val extractor = MediaExtractor()
            try {
                extractor.setDataSource(context, audio.uri, null)

                var audioTrackIdx = -1
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIdx = i
                        break
                    }
                }

                if (audioTrackIdx >= 0) {
                    extractor.selectTrack(audioTrackIdx)
                    val mime = extractor.getTrackFormat(audioTrackIdx).getString(MediaFormat.KEY_MIME)!!
                    val codec = MediaCodec.createDecoderByType(mime)
                    codec.configure(extractor.getTrackFormat(audioTrackIdx), null, null, 0)
                    codec.start()

                    val bufferInfo = MediaCodec.BufferInfo()
                    val rawAmplitudes = mutableListOf<Float>()
                    var isEOS = false

                    while (!isEOS && rawAmplitudes.size < 600) {
                        val inputIdx = codec.dequeueInputBuffer(5000L)
                        if (inputIdx >= 0) {
                            val inputBuffer = codec.getInputBuffer(inputIdx)
                            val sampleSize = if (inputBuffer != null) extractor.readSampleData(inputBuffer, 0) else -1
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(
                                    inputIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                )
                                isEOS = true
                            } else {
                                val sampleTime = extractor.sampleTime
                                codec.queueInputBuffer(
                                    inputIdx, 0, sampleSize, sampleTime, 0
                                )
                                extractor.advance()
                            }
                        }

                        val outputIdx = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                        if (outputIdx >= 0) {
                            val outputBuffer = codec.getOutputBuffer(outputIdx)
                            if (outputBuffer != null && bufferInfo.size > 0) {
                                var maxAmp = 0
                                val shortBuffer = outputBuffer.asShortBuffer()
                                while (shortBuffer.hasRemaining()) {
                                    val sample = abs(shortBuffer.get().toInt())
                                    if (sample > maxAmp) maxAmp = sample
                                }
                                rawAmplitudes.add(maxAmp.toFloat() / 32768f)
                            }
                            codec.releaseOutputBuffer(outputIdx, false)
                        }
                    }

                    codec.stop()
                    codec.release()

                    if (rawAmplitudes.isNotEmpty()) {
                        val binSize = (rawAmplitudes.size / sampleCount).coerceAtLeast(1)
                        val maxPeak = rawAmplitudes.maxOrNull()?.coerceAtLeast(0.01f) ?: 1f

                        for (b in 0 until sampleCount) {
                            val start = b * binSize
                            val end = ((b + 1) * binSize).coerceAtMost(rawAmplitudes.size)
                            if (start < end) {
                                val avg = rawAmplitudes.subList(start, end).average().toFloat()
                                peaks.add((avg / maxPeak).coerceIn(0.2f, 1.0f))
                            } else {
                                peaks.add(0.3f)
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { extractor.release() } catch (_: Exception) {}
            }

            if (peaks.isNotEmpty()) {
                waveformCache[cacheKey] = peaks
                withContext(Dispatchers.Main) {
                    onWaveformExtracted(peaks)
                }
            }
        }
    }
}

/** Serializes and deserializes [SavedProjectDraft] to JSON. */
object ProjectSerializer {

    fun toJson(draft: SavedProjectDraft): String {
        val root = JSONObject()
        root.put("id", draft.id)
        root.put("title", draft.title)
        root.put("lastModifiedMs", draft.lastModifiedMs)

        val p = draft.project
        val projJson = JSONObject()
        projJson.put("muteOriginalAudio", p.muteOriginalAudio)
        projJson.put("selectedLayerId", p.selectedLayerId ?: "")
        projJson.put("playheadPositionMs", p.playheadPositionMs)

        val setJson = JSONObject()
        setJson.put("resolution", p.canvasSettings.resolution)
        setJson.put("fps", p.canvasSettings.fps)
        setJson.put("aspectRatio", p.canvasSettings.aspectRatio)
        projJson.put("canvasSettings", setJson)

        val videoArr = JSONArray()
        p.videoClips.forEach { clip ->
            val v = JSONObject()
            v.put("id", clip.id)
            v.put("uri", clip.uri.toString())
            v.put("name", clip.name)
            v.put("sourceDurationMs", clip.sourceDurationMs)
            v.put("trimStartMs", clip.trimStartMs)
            v.put("trimEndMs", clip.trimEndMs)
            v.put("scale", clip.scale)
            v.put("translateXFraction", clip.translateXFraction)
            v.put("translateYFraction", clip.translateYFraction)
            v.put("rotationDegrees", clip.rotationDegrees)
            v.put("opacity", clip.opacity)
            v.put("isMirrored", clip.isMirrored)
            v.put("speed", clip.speed)
            v.put("transitionToNext", clip.transitionToNext.name)
            v.put("transitionDurationMs", clip.transitionDurationMs)
            v.put("filterPreset", clip.filterPreset.name)
            v.put("nativeWidthPx", clip.nativeWidthPx)
            v.put("nativeHeightPx", clip.nativeHeightPx)

            val kfArr = JSONArray()
            clip.keyframes.forEach { kf ->
                val k = JSONObject()
                k.put("id", kf.id)
                k.put("timeMs", kf.timeMs)
                k.put("scale", kf.scale)
                k.put("translateXFraction", kf.translateXFraction)
                k.put("translateYFraction", kf.translateYFraction)
                k.put("rotationDegrees", kf.rotationDegrees)
                k.put("opacity", kf.opacity)
                kfArr.put(k)
            }
            v.put("keyframes", kfArr)

            val c = JSONObject()
            c.put("left", clip.cropRect.left)
            c.put("top", clip.cropRect.top)
            c.put("right", clip.cropRect.right)
            c.put("bottom", clip.cropRect.bottom)
            c.put("aspectPreset", clip.cropRect.aspectPreset)
            v.put("cropRect", c)

            videoArr.put(v)
        }
        projJson.put("videoClips", videoArr)

        val audioArr = JSONArray()
        p.audioClips.forEach { audio ->
            val a = JSONObject()
            a.put("id", audio.id)
            a.put("uri", audio.uri.toString())
            a.put("name", audio.name)
            a.put("sourceDurationMs", audio.sourceDurationMs)
            a.put("trimStartMs", audio.trimStartMs)
            a.put("trimEndMs", audio.trimEndMs)
            a.put("startTimeMs", audio.startTimeMs)
            a.put("volume", audio.volume)
            a.put("isMuted", audio.isMuted)

            val beatArr = JSONArray()
            audio.beatMarkersMs.forEach { beatArr.put(it) }
            a.put("beatMarkersMs", beatArr)

            audioArr.put(a)
        }
        projJson.put("audioClips", audioArr)

        val textArr = JSONArray()
        p.textLayers.forEach { text ->
            val t = JSONObject()
            t.put("id", text.id)
            t.put("text", text.text)
            t.put("xFraction", text.xFraction)
            t.put("yFraction", text.yFraction)
            t.put("fontSizeSp", text.fontSizeSp)
            t.put("scale", text.scale)
            t.put("rotationDegrees", text.rotationDegrees)
            t.put("opacity", text.opacity)
            t.put("color", text.color.toArgb())
            t.put("backgroundColor", text.backgroundColor.toArgb())
            t.put("hasShadow", text.hasShadow)
            t.put("animation", text.animation.name)
            t.put("startTimeMs", text.startTimeMs)
            t.put("durationMs", text.durationMs)
            textArr.put(t)
        }
        projJson.put("textLayers", textArr)

        val imageArr = JSONArray()
        p.imageOverlays.forEach { img ->
            val o = JSONObject()
            o.put("id", img.id)
            o.put("uri", img.uri.toString())
            o.put("name", img.name)
            o.put("xFraction", img.xFraction)
            o.put("yFraction", img.yFraction)
            o.put("scale", img.scale)
            o.put("rotationDegrees", img.rotationDegrees)
            o.put("opacity", img.opacity)
            o.put("startTimeMs", img.startTimeMs)
            o.put("durationMs", img.durationMs)
            imageArr.put(o)
        }
        projJson.put("imageOverlays", imageArr)

        root.put("project", projJson)
        return root.toString()
    }

    fun fromJson(jsonStr: String): SavedProjectDraft {
        val root = JSONObject(jsonStr)
        val id = root.optString("id", UUID.randomUUID().toString())
        val title = root.optString("title", "Untitled Project")
        val lastModifiedMs = root.optLong("lastModifiedMs", System.currentTimeMillis())

        val projJson = root.getJSONObject("project")
        val muteOriginalAudio = projJson.optBoolean("muteOriginalAudio", false)
        val selectedLayerId = projJson.optString("selectedLayerId", "").takeIf { it.isNotEmpty() }
        val playheadPositionMs = projJson.optLong("playheadPositionMs", 0L)

        val setJson = projJson.optJSONObject("canvasSettings")
        val settings = if (setJson != null) {
            ExportSettings(
                resolution = setJson.optString("resolution", "1080p"),
                fps = setJson.optInt("fps", 30),
                aspectRatio = setJson.optString("aspectRatio", "9:16")
            )
        } else ExportSettings()

        val videoClips = mutableListOf<VideoClip>()
        val videoArr = projJson.optJSONArray("videoClips")
        if (videoArr != null) {
            for (i in 0 until videoArr.length()) {
                val v = videoArr.getJSONObject(i)
                val cJson = v.optJSONObject("cropRect")
                val crop = if (cJson != null) {
                    CropRect(
                        left = cJson.optDouble("left", 0.0).toFloat(),
                        top = cJson.optDouble("top", 0.0).toFloat(),
                        right = cJson.optDouble("right", 1.0).toFloat(),
                        bottom = cJson.optDouble("bottom", 1.0).toFloat(),
                        aspectPreset = cJson.optString("aspectPreset", "Free")
                    )
                } else CropRect()

                val transName = v.optString("transitionToNext", "NONE")
                val transition = try { TransitionType.valueOf(transName) } catch (_: Exception) { TransitionType.NONE }

                val filterName = v.optString("filterPreset", "NONE")
                val filterPreset = try { ColorFilterPreset.valueOf(filterName) } catch (_: Exception) { ColorFilterPreset.NONE }

                val keyframes = mutableListOf<TransformKeyframe>()
                val kfArr = v.optJSONArray("keyframes")
                if (kfArr != null) {
                    for (kIdx in 0 until kfArr.length()) {
                        val k = kfArr.getJSONObject(kIdx)
                        keyframes.add(
                            TransformKeyframe(
                                id = k.optString("id", UUID.randomUUID().toString()),
                                timeMs = k.optLong("timeMs", 0L),
                                scale = k.optDouble("scale", 1.0).toFloat(),
                                translateXFraction = k.optDouble("translateXFraction", 0.0).toFloat(),
                                translateYFraction = k.optDouble("translateYFraction", 0.0).toFloat(),
                                rotationDegrees = k.optDouble("rotationDegrees", 0.0).toFloat(),
                                opacity = k.optDouble("opacity", 1.0).toFloat()
                            )
                        )
                    }
                }

                videoClips.add(
                    VideoClip(
                        id = v.optString("id", UUID.randomUUID().toString()),
                        uri = Uri.parse(v.getString("uri")),
                        name = v.optString("name", "Clip"),
                        sourceDurationMs = v.optLong("sourceDurationMs", 0L),
                        trimStartMs = v.optLong("trimStartMs", 0L),
                        trimEndMs = v.optLong("trimEndMs", 0L),
                        cropRect = crop,
                        scale = v.optDouble("scale", 1.0).toFloat(),
                        translateXFraction = v.optDouble("translateXFraction", 0.0).toFloat(),
                        translateYFraction = v.optDouble("translateYFraction", 0.0).toFloat(),
                        rotationDegrees = v.optDouble("rotationDegrees", 0.0).toFloat(),
                        opacity = v.optDouble("opacity", 1.0).toFloat(),
                        isMirrored = v.optBoolean("isMirrored", false),
                        speed = v.optDouble("speed", 1.0).toFloat(),
                        transitionToNext = transition,
                        transitionDurationMs = v.optLong("transitionDurationMs", 500L),
                        filterPreset = filterPreset,
                        keyframes = keyframes,
                        nativeWidthPx = v.optInt("nativeWidthPx", 0),
                        nativeHeightPx = v.optInt("nativeHeightPx", 0)
                    )
                )
            }
        }

        val audioClips = mutableListOf<AudioClip>()
        val audioArr = projJson.optJSONArray("audioClips")
        if (audioArr != null) {
            for (i in 0 until audioArr.length()) {
                val a = audioArr.getJSONObject(i)
                val beatArr = a.optJSONArray("beatMarkersMs")
                val beats = mutableListOf<Long>()
                if (beatArr != null) {
                    for (j in 0 until beatArr.length()) beats.add(beatArr.getLong(j))
                }

                audioClips.add(
                    AudioClip(
                        id = a.optString("id", UUID.randomUUID().toString()),
                        uri = Uri.parse(a.getString("uri")),
                        name = a.optString("name", "Audio"),
                        sourceDurationMs = a.optLong("sourceDurationMs", 0L),
                        trimStartMs = a.optLong("trimStartMs", 0L),
                        trimEndMs = a.optLong("trimEndMs", 0L),
                        startTimeMs = a.optLong("startTimeMs", 0L),
                        volume = a.optDouble("volume", 1.0).toFloat(),
                        isMuted = a.optBoolean("isMuted", false),
                        beatMarkersMs = beats
                    )
                )
            }
        }

        val textLayers = mutableListOf<TextOverlayItem>()
        val textArr = projJson.optJSONArray("textLayers")
        if (textArr != null) {
            for (i in 0 until textArr.length()) {
                val t = textArr.getJSONObject(i)
                val animName = t.optString("animation", "NONE")
                val anim = try { TextAnimation.valueOf(animName) } catch (_: Exception) { TextAnimation.NONE }

                textLayers.add(
                    TextOverlayItem(
                        id = t.optString("id", UUID.randomUUID().toString()),
                        text = t.optString("text", "Text"),
                        xFraction = t.optDouble("xFraction", 0.5).toFloat(),
                        yFraction = t.optDouble("yFraction", 0.5).toFloat(),
                        fontSizeSp = t.optDouble("fontSizeSp", 28.0).toFloat(),
                        scale = t.optDouble("scale", 1.0).toFloat(),
                        rotationDegrees = t.optDouble("rotationDegrees", 0.0).toFloat(),
                        opacity = t.optDouble("opacity", 1.0).toFloat(),
                        color = Color(t.optInt("color", android.graphics.Color.WHITE)),
                        backgroundColor = Color(t.optInt("backgroundColor", android.graphics.Color.TRANSPARENT)),
                        hasShadow = t.optBoolean("hasShadow", true),
                        animation = anim,
                        startTimeMs = t.optLong("startTimeMs", 0L),
                        durationMs = t.optLong("durationMs", 10000L)
                    )
                )
            }
        }

        val imageOverlays = mutableListOf<ImageOverlayItem>()
        val imageArr = projJson.optJSONArray("imageOverlays")
        if (imageArr != null) {
            for (i in 0 until imageArr.length()) {
                val o = imageArr.getJSONObject(i)
                imageOverlays.add(
                    ImageOverlayItem(
                        id = o.optString("id", UUID.randomUUID().toString()),
                        uri = Uri.parse(o.getString("uri")),
                        name = o.optString("name", "Overlay"),
                        xFraction = o.optDouble("xFraction", 0.5).toFloat(),
                        yFraction = o.optDouble("yFraction", 0.5).toFloat(),
                        scale = o.optDouble("scale", 1.0).toFloat(),
                        rotationDegrees = o.optDouble("rotationDegrees", 0.0).toFloat(),
                        opacity = o.optDouble("opacity", 1.0).toFloat(),
                        startTimeMs = o.optLong("startTimeMs", 0L),
                        durationMs = o.optLong("durationMs", 10000L)
                    )
                )
            }
        }

        return SavedProjectDraft(
            id = id,
            title = title,
            lastModifiedMs = lastModifiedMs,
            project = CanvasProject(
                videoClips = videoClips,
                audioClips = audioClips,
                textLayers = textLayers,
                imageOverlays = imageOverlays,
                muteOriginalAudio = muteOriginalAudio,
                selectedLayerId = selectedLayerId,
                playheadPositionMs = playheadPositionMs,
                canvasSettings = settings
            )
        )
    }
}

/**
 * Manages saving, loading, and deleting project drafts in app internal files.
 */
object ProjectStorageManager {

    private fun getProjectsDir(context: Context): File {
        val dir = File(context.filesDir, "projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun saveDraft(context: Context, draft: SavedProjectDraft) {
        try {
            val dir = getProjectsDir(context)
            val file = File(dir, "${draft.id}.json")
            val json = ProjectSerializer.toJson(draft)
            file.writeText(json)
        } catch (_: Exception) {}
    }

    fun loadAllDrafts(context: Context): List<SavedProjectDraft> {
        return try {
            val dir = getProjectsDir(context)
            val files = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
            files.mapNotNull { file ->
                try {
                    val json = file.readText()
                    ProjectSerializer.fromJson(json)
                } catch (_: Exception) {
                    null
                }
            }.sortedByDescending { it.lastModifiedMs }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun deleteDraft(context: Context, draftId: String) {
        try {
            val dir = getProjectsDir(context)
            val file = File(dir, "$draftId.json")
            if (file.exists()) file.delete()
        } catch (_: Exception) {}
    }
}

/** Open editor tool panel. */
enum class EditorTool {
    NONE, CLIPS, TRIM, SPLIT, TRANSFORM, TEXT, AUDIO, SPEED, TRANSITIONS, FILTERS, KEYFRAMES, OVERLAY
}

/** Export progress state. */
data class ExportState(
    val isExporting: Boolean = false,
    val progress: Float = 0f,
    val resultMessage: String? = null,
    val exportedUri: Uri? = null,
    val isError: Boolean = false
)

/** Given a clip's native resolution and the project's target aspect ratio, computes
 *  the extra zoom (on top of normal letterbox-fit) needed so the clip fully covers
 *  the canvas with no blank bars ("fit to canvas" / cover-crop). Returns 1f if unknown. */
fun computeFitToCanvasScale(nativeWidthPx: Int, nativeHeightPx: Int, canvasAspectRatio: String): Float {
    if (nativeWidthPx <= 0 || nativeHeightPx <= 0) return 1f

    val boxAspect = when (canvasAspectRatio) {
        "16:9" -> 16f / 9f
        "9:16" -> 9f / 16f
        "1:1" -> 1f
        else -> nativeWidthPx.toFloat() / nativeHeightPx.toFloat()
    }
    val videoAspect = nativeWidthPx.toFloat() / nativeHeightPx.toFloat()

    val fit = minOf(boxAspect / videoAspect, 1f)
    val cover = maxOf(boxAspect / videoAspect, 1f)
    return if (fit <= 0f) 1f else (cover / fit)
}