package com.example.canvas

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.opengl.Matrix
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.compose.ui.graphics.Color
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.RgbMatrix
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.SpeedChangeEffect
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.UUID

/**
 * ==============================================================================
 * VIDEO EXPORT MANAGER (AndroidX Media3 Transformer Engine)
 * ==============================================================================
 *
 * College PBL Viva Summary:
 * ------------------------------------------------------------------------------
 * 1. Multi-Video Processing: Uses [EditedMediaItemSequence] to concatenate
 *    multiple [VideoClip] instances into a seamless video track.
 * 2. Media3 Effects Pipeline:
 *    - Crop Effect: Applies OpenGL [Crop] vertex shader transformations (-1..1 NDC).
 *    - Scale & Rotate: Uses [ScaleAndRotateTransformation] for zoom & rotation
 *      (this is also where a clip's "Fit to Canvas" auto-zoom, computed in
 *      EditorModels.kt's computeFitToCanvasScale, actually gets baked in).
 *    - Text Overlays: Renders styled text bitmaps into an [OverlayEffect].
 * 3. Multi-Audio Mixing: Combines background music tracks in parallel [EditedMediaItemSequence]s.
 * 4. Storage & MediaStore: Writes output MP4 into shared Movies/Canvas directory
 *    and triggers [MediaScannerConnection] for instant Gallery indexing.
 *
 * NOTE: text overlay start/duration (per-layer visibility, i.e. "only show this
 * caption for part of the video") is a preview-time + export-time concept.
 * Baking that into the OverlayEffect precisely (rather than showing it for the
 * whole clip) needs a time-windowed overlay; Media3's OverlayEffect renders a
 * BitmapOverlay for the entire composition by default. A pragmatic way to
 * respect startTimeMs/durationMs on export is to only include overlays whose
 * window covers a *sequence-based* windowing effect, which Media3 doesn't
 * expose directly per-bitmap. This build keeps overlays showing for the whole
 * export (same as before) but the LIVE PREVIEW now correctly respects
 * startTimeMs/durationMs so you can see exactly when each caption should
 * appear — if exact per-caption export timing turns out to matter for your
 * viva, the most reliable route is exporting your video track in per-caption
 * segments (matching the timeline segments you already split), each with its
 * own OverlayEffect, which is a natural next step once you're comfortable
 * with this file.
 */
private data class ExportVideoSequenceInfo(
    val startMs: Long,
    val durationMs: Long,
    val transitionToNext: TransitionType,
    val transitionDurationMs: Long
)

private class TimelineVideoCompositorSettings(
    private val sequenceInfo: List<ExportVideoSequenceInfo>,
    private val outputWidth: Int,
    private val outputHeight: Int
) : androidx.media3.common.VideoCompositorSettings {
    override fun getOutputSize(inputSizes: List<Size>): Size {
        val fallback = inputSizes.firstOrNull() ?: Size(1, 1)
        return Size(
            outputWidth.takeIf { it > 0 } ?: fallback.width,
            outputHeight.takeIf { it > 0 } ?: fallback.height
        )
    }

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): androidx.media3.common.OverlaySettings {
        val info = sequenceInfo.getOrNull(inputId) ?: return StaticOverlaySettings.Builder().build()
        val timeMs = (presentationTimeUs / 1000L - info.startMs).coerceAtLeast(0L)
        val durationMs = info.transitionDurationMs.coerceAtLeast(1L)
        val transitionStartMs = (info.durationMs - durationMs).coerceAtLeast(0L)

        var alpha = 1f
        var horizontalOffset = 0f

        if (info.transitionToNext != TransitionType.NONE && timeMs >= transitionStartMs) {
            val progress = ((timeMs - transitionStartMs).toFloat() / durationMs).coerceIn(0f, 1f)
            when (info.transitionToNext) {
                TransitionType.CROSSFADE, TransitionType.FADE_BLACK -> alpha = 1f - progress
                TransitionType.SLIDE_LEFT -> horizontalOffset = -progress
                TransitionType.SLIDE_RIGHT -> horizontalOffset = progress
                TransitionType.NONE -> Unit
            }
        }

        val previous = sequenceInfo.getOrNull(inputId - 1)
        val incomingDurationMs = previous?.transitionDurationMs?.coerceAtLeast(1L) ?: durationMs
        if (previous != null && previous.transitionToNext != TransitionType.NONE && timeMs < incomingDurationMs) {
            val progress = (timeMs.toFloat() / incomingDurationMs).coerceIn(0f, 1f)
            when (previous.transitionToNext) {
                TransitionType.CROSSFADE -> alpha = progress
                TransitionType.SLIDE_LEFT -> horizontalOffset = 1f - progress
                TransitionType.SLIDE_RIGHT -> horizontalOffset = -(1f - progress)
                TransitionType.FADE_BLACK -> alpha = progress
                TransitionType.NONE -> Unit
            }
        }

        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha.coerceIn(0f, 1f))
            .setBackgroundFrameAnchor(0f, 0f)
            .setOverlayFrameAnchor(horizontalOffset.coerceIn(-1f, 1f), 0f)
            .build()
    }
}

@UnstableApi
object VideoExportManager {

    fun export(
        context: Context,
        videoClips: List<VideoClip>,
        textOverlays: List<TextOverlayItem>,
        imageOverlays: List<ImageOverlayItem> = emptyList(),
        audioClips: List<AudioClip>,
        muteOriginalAudio: Boolean,
        exportSettings: ExportSettings = ExportSettings(),
        onProgress: (Float) -> Unit,
        onComplete: (Uri) -> Unit,
        onError: (Exception) -> Unit
    ) {
        if (videoClips.isEmpty()) {
            onError(IllegalArgumentException("No video clips to export"))
            return
        }

        val outputFile = File(context.cacheDir, "canvas_export_${System.currentTimeMillis()}.mp4")
        try {
        val resolvedUris = mutableMapOf<String, Uri>()
        fun resolveUri(uri: Uri, type: String): Uri {
            if (uri.scheme != "content") return uri
            return resolvedUris.getOrPut(uri.toString()) {
                val directory = File(context.cacheDir, "export_assets").apply { mkdirs() }
                val destination = File(directory, "${type}_${UUID.randomUUID()}")
                try {
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        destination.outputStream().use { output -> input.copyTo(output) }
                    } ?: throw IllegalStateException("Cannot read $type asset: $uri")
                    Uri.fromFile(destination)
                } catch (exception: Exception) {
                    throw IllegalStateException("Cannot prepare $type asset for export", exception)
                }
            }
        }

        fun requireReadable(uri: Uri, type: String) {
            if (uri.scheme == "file" && !File(uri.path ?: "").canRead()) {
                throw IllegalStateException("Cannot read $type asset: ${uri.lastPathSegment ?: uri}")
            }
        }

        videoClips.forEach { clip ->
            requireReadable(resolveUri(clip.uri, "video"), "video")
        }
        audioClips.filterNot { it.isMuted }.forEach { audio ->
            requireReadable(resolveUri(audio.uri, "audio"), "audio")
        }
        imageOverlays.forEach { image ->
            requireReadable(resolveUri(image.uri, "image"), "image")
        }

        val (videoWidth, videoHeight) = getVideoDimensions(context, resolveUri(videoClips.first().uri, "video"))

        val textureOverlays = mutableListOf<TextureOverlay>()
        textOverlays.forEach { text ->
            textureOverlays.add(bitmapOverlayFor(text, videoWidth, videoHeight))
        }
        imageOverlays.forEach { img ->
            val imgOverlay = imageOverlayFor(
                context,
                img.copy(uri = resolveUri(img.uri, "image")),
                videoWidth,
                videoHeight
            )
            if (imgOverlay != null) textureOverlays.add(imgOverlay)
        }

        val videoEffects = if (textureOverlays.isEmpty()) {
            emptyList()
        } else {
            listOf(OverlayEffect(textureOverlays))
        }

        // --- Step 1: Process each Video Clip with Trimming, Crop, Zoom & Rotation ---
        val editedVideoItems = videoClips.mapIndexed { index, clip ->
            val clippedMediaItem = MediaItem.Builder()
                .setUri(resolveUri(clip.uri, "video"))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
                .build()

            val clipEffects = mutableListOf<Effect>()

            // 1A. Apply Normalized OpenGL Crop Vertex Shader (-1.0 to 1.0)
            val crop = clip.cropRect
            if (crop.left > 0f || crop.top > 0f || crop.right < 1f || crop.bottom < 1f) {
                val leftNdc = (-1f + (crop.left * 2f)).coerceIn(-1f, 0.9f)
                val rightNdc = (-1f + (crop.right * 2f)).coerceIn(leftNdc + 0.1f, 1f)
                val topNdc = (1f - (crop.top * 2f)).coerceIn(-0.9f, 1f)
                val bottomNdc = (1f - (crop.bottom * 2f)).coerceIn(-1f, topNdc - 0.1f)
                clipEffects.add(Crop(leftNdc, rightNdc, bottomNdc, topNdc))
            }

            // 1B. Apply Zoom (Scale), Horizontal Mirror (Flip), & Rotation Transformation
            val absScale = clip.scale.coerceAtLeast(0.01f)
            val scaleX = if (clip.isMirrored) -absScale else absScale
            clipEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setScale(scaleX, absScale)
                    .setRotationDegrees(clip.rotationDegrees)
                    .build()
            )

            // 1C. Apply Color Filter Preset (Vivid, Vintage, B&W, Cinematic, Warm, Cool, Sepia)
            if (clip.filterPreset != ColorFilterPreset.NONE) {
                if (clip.filterPreset == ColorFilterPreset.B_AND_W) {
                    clipEffects.add(RgbFilter.createGrayscaleFilter())
                } else {
                    val preset = clip.filterPreset
                    val filterMatrix = getMedia3ColorMatrixArray(preset)
                    val filterEffect = object : RgbMatrix {
                        override fun getMatrix(presentationTimeUs: Long, useHdr: Boolean): FloatArray {
                            return filterMatrix
                        }
                    }
                    clipEffects.add(filterEffect)
                }
            }

            // 1D. Apply Speed Change Effect (0.5x to 4.0x)
            if (clip.speed != 1.0f && clip.speed > 0.05f) {
                clipEffects.add(SpeedChangeEffect(clip.speed))
            }

            // 1E. Append Composition Text Overlay Effects
            clipEffects.addAll(videoEffects)

            val videoItemBuilder = EditedMediaItem.Builder(clippedMediaItem)
                .setEffects(Effects(emptyList(), clipEffects))

            videoItemBuilder.setRemoveAudio(muteOriginalAudio)

            videoItemBuilder.build()
        }

        // --- Step 2: Assemble all video clips in one sequential video track ---
        val sequences = mutableListOf<EditedMediaItemSequence>()
        val sequenceInfo = mutableListOf<ExportVideoSequenceInfo>()
        sequences.add(
            EditedMediaItemSequence.Builder(editedVideoItems)
                .build()
        )

        // --- Step 3: Add Background Audio Tracks (Constrained to Total Video Duration) ---
        val totalVideoDurationMs = videoClips.sumOf { it.trimmedDurationMs }.coerceAtLeast(1000L)

        audioClips.forEach { audio ->
            if (!audio.isMuted) {
                val startMs = audio.trimStartMs
                val maxEndMs = startMs + totalVideoDurationMs
                val endMs = audio.trimEndMs.coerceAtMost(maxEndMs).coerceAtLeast(startMs + 100L)

                val audioMediaItem = MediaItem.Builder()
                    .setUri(resolveUri(audio.uri, "audio"))
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(startMs)
                            .setEndPositionMs(endMs)
                            .build()
                    )
                    .build()

                val audioEditedItem = EditedMediaItem.Builder(audioMediaItem)
                    .setRemoveVideo(true)
                    .build()

                sequences.add(
                    EditedMediaItemSequence.Builder(listOf(audioEditedItem))
                        .experimentalSetForceAudioTrack(true)
                        .build()
                )
            }
        }

        // --- Step 4: Build Media3 Composition ---
        val composition = Composition.Builder(sequences).build()
        val mainHandler = Handler(Looper.getMainLooper())

        // --- Step 5: Start Asynchronous Media3 Transformer Job ---
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    try {
                        val savedUri = saveToGallery(context, outputFile)
                        onComplete(savedUri)
                    } catch (exception: Exception) {
                        if (outputFile.exists()) outputFile.delete()
                        onError(exception)
                    }
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (outputFile.exists()) outputFile.delete()
                    onError(exportException)
                }
            })
            .build()

        transformer.start(composition, outputFile.absolutePath)

        // Poll progress every 300ms
        val progressHolder = ProgressHolder()
        val poller = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress(progressHolder.progress / 100f)
                }
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED &&
                    state != Transformer.PROGRESS_STATE_UNAVAILABLE
                ) {
                    mainHandler.postDelayed(this, 300)
                }
            }
        }
        mainHandler.postDelayed(poller, 300)
        } catch (exception: Exception) {
            if (outputFile.exists()) outputFile.delete()
            onError(exception)
        }
    }

    private fun getMedia3ColorMatrixArray(preset: ColorFilterPreset): FloatArray {
        val matrix = FloatArray(16)
        Matrix.setIdentityM(matrix, 0)
        when (preset) {
            ColorFilterPreset.VIVID -> {
                matrix[0] = 1.3f; matrix[5] = 1.3f; matrix[10] = 1.3f
            }
            ColorFilterPreset.VINTAGE -> {
                matrix[0] = 0.9f; matrix[1] = 0.2f; matrix[2] = 0.1f
                matrix[4] = 0.1f; matrix[5] = 0.8f; matrix[6] = 0.1f
                matrix[8] = 0.1f; matrix[9] = 0.1f; matrix[10] = 0.6f
            }
            ColorFilterPreset.B_AND_W -> {
                matrix[0] = 0.33f; matrix[1] = 0.59f; matrix[2] = 0.11f
                matrix[4] = 0.33f; matrix[5] = 0.59f; matrix[6] = 0.11f
                matrix[8] = 0.33f; matrix[9] = 0.59f; matrix[10] = 0.11f
            }
            ColorFilterPreset.CINEMATIC_TEAL_ORANGE -> {
                matrix[0] = 1.2f; matrix[1] = 0.1f; matrix[2] = 0f
                matrix[4] = 0.1f; matrix[5] = 0.9f; matrix[6] = 0.2f
                matrix[8] = 0f;    matrix[9] = 0.2f; matrix[10] = 1.3f
            }
            ColorFilterPreset.WARM -> {
                matrix[0] = 1.2f; matrix[1] = 0.1f; matrix[2] = 0f
                matrix[4] = 0.1f; matrix[5] = 1.1f; matrix[6] = 0f
                matrix[8] = 0f;    matrix[9] = 0f;   matrix[10] = 0.8f
            }
            ColorFilterPreset.COOL -> {
                matrix[0] = 0.8f; matrix[1] = 0f;   matrix[2] = 0.1f
                matrix[4] = 0f;   matrix[5] = 0.9f; matrix[6] = 0.2f
                matrix[8] = 0.1f; matrix[9] = 0.2f; matrix[10] = 1.3f
            }
            ColorFilterPreset.SEPIA -> {
                matrix[0] = 0.393f; matrix[1] = 0.769f; matrix[2] = 0.189f
                matrix[4] = 0.349f; matrix[5] = 0.686f; matrix[6] = 0.168f
                matrix[8] = 0.272f; matrix[9] = 0.534f; matrix[10] = 0.131f
            }
            ColorFilterPreset.NONE -> {}
        }
        return matrix
    }

    private fun imageOverlayFor(
        context: Context,
        overlay: ImageOverlayItem,
        videoWidth: Int,
        videoHeight: Int
    ): BitmapOverlay? {
        return try {
            val w = if (videoWidth > 0) videoWidth else 1080
            val h = if (videoHeight > 0) videoHeight else 1920

            val inputStream = context.contentResolver.openInputStream(overlay.uri) ?: return null
            val srcBitmap = BitmapFactory.decodeStream(inputStream) ?: return null

            val targetBitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(targetBitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                alpha = (overlay.opacity.coerceIn(0f, 1f) * 255).toInt()
            }

            val x = overlay.xFraction * w
            val y = overlay.yFraction * h

            canvas.save()
            canvas.rotate(overlay.rotationDegrees, x, y)
            canvas.scale(overlay.scale, overlay.scale, x, y)

            val left = x - srcBitmap.width / 2f
            val top = y - srcBitmap.height / 2f
            canvas.drawBitmap(srcBitmap, left, top, paint)
            canvas.restore()

            srcBitmap.recycle()

            BitmapOverlay.createStaticBitmapOverlay(targetBitmap)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Renders a text overlay item onto a transparent Android [Bitmap]
     * matching the video resolution, applying scale, rotation, opacity, background box, and shadow.
     */
    private fun bitmapOverlayFor(
        overlay: TextOverlayItem,
        videoWidth: Int,
        videoHeight: Int
    ): BitmapOverlay {
        val w = if (videoWidth > 0) videoWidth else 1080
        val h = if (videoHeight > 0) videoHeight else 1920

        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = androidColorFromCompose(overlay.color)
            textSize = overlay.fontSizeSp * (w / 400f)
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            alpha = (overlay.opacity.coerceIn(0f, 1f) * 255).toInt()
            if (overlay.hasShadow) {
                setShadowLayer(8f, 2f, 2f, AndroidColor.BLACK)
            }
        }

        val x = overlay.xFraction * w
        val y = overlay.yFraction * h

        canvas.save()
        canvas.rotate(overlay.rotationDegrees, x, y)
        canvas.scale(overlay.scale, overlay.scale, x, y)

        if (overlay.backgroundColor != Color.Transparent) {
            val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = androidColorFromCompose(overlay.backgroundColor)
                alpha = (overlay.opacity.coerceIn(0f, 1f) * 255).toInt()
            }
            val textWidth = paint.measureText(overlay.text)
            val textHeight = paint.textSize
            val rect = RectF(
                x - textWidth / 2f - 16f,
                y - textHeight + 4f,
                x + textWidth / 2f + 16f,
                y + 12f
            )
            canvas.drawRoundRect(rect, 12f, 12f, bgPaint)
        }

        canvas.drawText(overlay.text, x, y, paint)
        canvas.restore()

        val settings = StaticOverlaySettings.Builder().build()
        return BitmapOverlay.createStaticBitmapOverlay(bitmap, settings)
    }

    private fun androidColorFromCompose(color: Color): Int {
        return AndroidColor.argb(
            (color.alpha * 255).toInt(),
            (color.red * 255).toInt(),
            (color.green * 255).toInt(),
            (color.blue * 255).toInt()
        )
    }

    fun getVideoDimensions(context: Context, uri: Uri): Pair<Int, Int> {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
            val rotation = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            if (rotation == 90 || rotation == 270) h to w else w to h
        } catch (_: Exception) {
            0 to 0
        } finally {
            retriever.release()
        }
    }

    fun getVideoDurationMs(context: Context, uri: Uri): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    /**
     * Saves the exported MP4 file into the shared Movies/Canvas collection
     * and registers it with [MediaScannerConnection] for immediate Gallery visibility.
     */
    private fun saveToGallery(context: Context, file: File): Uri {
        val resolver = context.contentResolver
        val fileName = "canvas_${System.currentTimeMillis()}.mp4"

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Canvas")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val itemUri = resolver.insert(collection, values)
                ?: throw IllegalStateException("Could not create MediaStore entry")

            resolver.openOutputStream(itemUri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }

            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)

            try {
                resolver.query(itemUri, arrayOf(MediaStore.Video.Media.DATA), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val dataIdx = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                        if (dataIdx >= 0) {
                            val path = cursor.getString(dataIdx)
                            if (!path.isNullOrEmpty()) {
                                MediaScannerConnection.scanFile(
                                    context,
                                    arrayOf(path),
                                    arrayOf("video/mp4"),
                                    null
                                )
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            file.delete()
            return itemUri
        } else {
            val targetDir = File(
                Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_MOVIES
                ),
                "Canvas"
            )
            if (!targetDir.exists()) targetDir.mkdirs()
            val destFile = File(targetDir, fileName)
            file.copyTo(destFile, overwrite = true)
            file.delete()

            var scannedUri: Uri = Uri.fromFile(destFile)
            MediaScannerConnection.scanFile(
                context,
                arrayOf(destFile.absolutePath),
                arrayOf("video/mp4")
            ) { _, uri ->
                if (uri != null) scannedUri = uri
            }
            return scannedUri
        }
    }
}