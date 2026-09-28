package com.example.canvas

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.example.canvas.ui.theme.CanvasEditorColors
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOutMap
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.random.Random

// Helper for formatting timestamp as 00:03.42
fun formatMsDetailed(ms: Long): String {
    val totalMs = ms.coerceAtLeast(0L)
    val totalSeconds = totalMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hundredths = (totalMs % 1000) / 10
    return "%02d:%02d.%02d".format(minutes, seconds, hundredths)
}

// ============================================================
// MY PROJECTS DRAFTS SCREEN
// ============================================================

@Composable
fun ProjectsScreen(
    drafts: List<SavedProjectDraft>,
    onSelectDraft: (SavedProjectDraft) -> Unit,
    onCreateNewProject: () -> Unit,
    onDeleteDraft: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F0F0F))
            .padding(20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Canvas",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "My Project Drafts",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
            }

            Button(onClick = onCreateNewProject) {
                Icon(Icons.Default.Add, contentDescription = "New Project")
                Spacer(modifier = Modifier.width(6.dp))
                Text("New Project")
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (drafts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF181818), RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No saved drafts yet",
                        color = Color.LightGray,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Projects auto-save so you can resume editing anytime.",
                        color = Color.Gray,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(20.dp))
                    Button(onClick = onCreateNewProject) {
                        Icon(Icons.Default.Add, contentDescription = "Create Project")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create First Project")
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                drafts.forEach { draft ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF181818), RoundedCornerShape(16.dp))
                            .clickable { onSelectDraft(draft) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = draft.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${draft.project.videoClips.size} Video Clips • ${formatMsDetailed(draft.project.totalDurationMs)}",
                                color = Color.LightGray,
                                fontSize = 12.sp
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Button(onClick = { onSelectDraft(draft) }) {
                                Text("Resume")
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(onClick = { onDeleteDraft(draft.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete Draft", tint = Color(0xFFFF6B6B))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ============================================================
// GENERIC DRAGGABLE TIMELINE BLOCK WITH MAGNETIC SNAPPING
// ============================================================

@Composable
private fun DraggableTimelineBlock(
    id: String,
    label: String,
    startMs: Long,
    durationMs: Long,
    totalDurationMs: Long,
    trackWidthPx: Float,
    snapTargetsMs: List<Long>,
    height: Dp,
    color: Color,
    selectedColor: Color,
    isSelected: Boolean,
    allowResize: Boolean,
    minDurationMs: Long = 300L,
    onSelect: () -> Unit,
    onMove: (newStartMs: Long) -> Unit,
    onResize: ((newDurationMs: Long) -> Unit)? = null,
    onDelete: () -> Unit,
    extraAction: (@Composable RowScope.() -> Unit)? = null,
    content: (@Composable () -> Unit)? = null
) {
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var localStart by remember(id, startMs) { mutableStateOf(startMs) }
    var localDuration by remember(id, durationMs) { mutableStateOf(durationMs) }

    var accumulatedDragPx by remember(id) { mutableFloatStateOf(0f) }
    var accumulatedResizePx by remember(id) { mutableFloatStateOf(0f) }

    val safeTotal = totalDurationMs.coerceAtLeast(1L)
    val msPerPx = if (trackWidthPx > 0f) safeTotal.toFloat() / trackWidthPx else 1f
    val offsetPx = (localStart / safeTotal.toFloat()) * trackWidthPx
    val widthPx = ((localDuration / safeTotal.toFloat()) * trackWidthPx).coerceAtLeast(36f)

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetPx.toInt(), 0) }
            .width(with(density) { widthPx.toDp() })
            .height(height)
            .background(if (isSelected) selectedColor else color, RoundedCornerShape(6.dp))
            .then(
                if (isSelected) Modifier.border(2.dp, Color.White, RoundedCornerShape(6.dp))
                else Modifier.border(0.5.dp, Color.Black.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            )
            .pointerInput(id) {
                detectTapGestures(
                    onTap = { onSelect() }
                )
            }
            .pointerInput(id, trackWidthPx, safeTotal) {
                detectDragGestures(
                    onDragStart = {
                        accumulatedDragPx = 0f
                        onSelect()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        accumulatedDragPx += dragAmount.x
                        val deltaMs = (accumulatedDragPx * msPerPx).toLong()
                        if (deltaMs != 0L) {
                            accumulatedDragPx -= (deltaMs / msPerPx)
                            var candidateMs = (localStart + deltaMs).coerceAtLeast(0L)

                            val snapThresholdMs = (250L * msPerPx).toLong().coerceIn(100L, 350L)
                            for (target in snapTargetsMs) {
                                if (abs(candidateMs - target) <= snapThresholdMs) {
                                    if (candidateMs != target) {
                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    }
                                    candidateMs = target
                                    break
                                }
                            }

                            localStart = candidateMs
                            onMove(localStart)
                        }
                    }
                )
            }
    ) {
        content?.invoke()

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 6.dp, end = if (allowResize) 18.dp else 6.dp)
        ) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier.weight(1f, fill = false)
            )
            if (isSelected) extraAction?.invoke(this)
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 6.dp, y = (-8).dp)
                    .size(22.dp)
                    .background(CanvasEditorColors.DeleteBadge, CircleShape)
                    .border(1.dp, Color.White, CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Layer", tint = Color.White, modifier = Modifier.size(12.dp))
            }
        }

        if (allowResize && onResize != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(16.dp)
                    .fillMaxHeight()
                    .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                    .pointerInput(id, trackWidthPx, safeTotal) {
                        detectDragGestures(
                            onDragStart = { accumulatedResizePx = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                accumulatedResizePx += dragAmount.x
                                val deltaMs = (accumulatedResizePx * msPerPx).toLong()
                                if (deltaMs != 0L) {
                                    accumulatedResizePx -= (deltaMs / msPerPx)
                                    localDuration = (localDuration + deltaMs).coerceAtLeast(minDurationMs)
                                    onResize(localDuration)
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(Color.DarkGray)
                )
            }
        }
    }
}

// ============================================================
// MULTI-TRACK TIMELINE VIEW WITH FILMSTRIPS, WAVEFORMS, TRANSITIONS & OVERLAYS
// ============================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineView(
    videoClips: List<VideoClip>,
    selectedVideoClipId: String?,
    audioClips: List<AudioClip>,
    selectedAudioClipId: String?,
    textOverlays: List<TextOverlayItem>,
    selectedTextOverlayId: String?,
    imageOverlays: List<ImageOverlayItem> = emptyList(),
    selectedImageOverlayId: String? = null,
    currentTimeMs: Long,
    isPlaying: Boolean,
    onPlayPauseToggle: () -> Unit,
    onSeekTo: (positionMs: Long) -> Unit,
    onSelectVideoClip: (id: String) -> Unit,
    onDeleteVideoClip: (id: String) -> Unit = {},
    onSelectAudioClip: (id: String) -> Unit,
    onSelectTextOverlay: (id: String) -> Unit,
    onSelectImageOverlay: (id: String) -> Unit = {},
    onSelectTransition: ((clipId: String) -> Unit)? = null,
    onAddVideo: () -> Unit = {},
    onAddAudio: () -> Unit = {},
    onAddText: () -> Unit = {},
    onAddImageOverlay: () -> Unit = {},
    canvasAspectRatio: String = "9:16",
    onSplitSelectedAtPlayhead: () -> Unit = {},
    onMoveTextLayer: (id: String, newStartMs: Long) -> Unit = { _, _ -> },
    onResizeTextLayer: (id: String, newDurationMs: Long) -> Unit = { _, _ -> },
    onDeleteTextLayer: (id: String) -> Unit = {},
    onMoveImageOverlay: (id: String, newStartMs: Long) -> Unit = { _, _ -> },
    onResizeImageOverlay: (id: String, newDurationMs: Long) -> Unit = { _, _ -> },
    onDeleteImageOverlay: (id: String) -> Unit = {},
    onSplitAudioAt: (audioId: String, positionMs: Long) -> Unit = { _, _ -> },
    onMoveAudioClip: (id: String, newStartMs: Long) -> Unit = { _, _ -> },
    onDeleteAudioClip: (id: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val totalDurationMs = videoClips.sumOf { it.trimmedDurationMs }.coerceAtLeast(1000L)

    var zoomScale by remember { mutableFloatStateOf(1.0f) }

    val snapTargetsMs = remember(videoClips, audioClips, currentTimeMs) {
        val targets = mutableListOf<Long>()
        targets.add(currentTimeMs)

        var videoOffsetMs = 0L
        videoClips.forEach { clip ->
            targets.add(videoOffsetMs)
            videoOffsetMs += clip.trimmedDurationMs
            targets.add(videoOffsetMs)
        }

        audioClips.forEach { audio ->
            targets.addAll(audio.beatMarkersMs)
        }

        targets.distinct().sorted()
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF121212), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${formatMsDetailed(currentTimeMs)} / ${formatMsDetailed(totalDurationMs)}",
                    color = Color.LightGray,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )

                val hasSelectedLayer = (selectedVideoClipId != null && videoClips.size > 1) ||
                        selectedAudioClipId != null ||
                        selectedTextOverlayId != null ||
                        selectedImageOverlayId != null

                if (hasSelectedLayer) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CanvasEditorColors.DeleteBadge)
                            .clickable {
                                if (selectedVideoClipId != null && videoClips.size > 1) onDeleteVideoClip(selectedVideoClipId)
                                else if (selectedAudioClipId != null) onDeleteAudioClip(selectedAudioClipId)
                                else if (selectedTextOverlayId != null) onDeleteTextLayer(selectedTextOverlayId)
                                else if (selectedImageOverlayId != null) onDeleteImageOverlay(selectedImageOverlayId)
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Selected", tint = Color.White, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            IconButton(
                onClick = onPlayPauseToggle,
                modifier = Modifier
                    .size(36.dp)
                    .background(Color(0xFF2A2A2A), CircleShape)
            ) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                IconButton(onClick = onAddVideo, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Add, contentDescription = "Add Video", tint = CanvasEditorColors.AccentPurple)
                }
                IconButton(onClick = onAddAudio, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MusicNote, contentDescription = "Add Audio", tint = Color(0xFFFFB830))
                }
                IconButton(onClick = onAddText, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.TextFields, contentDescription = "Add Text", tint = Color(0xFF10B981))
                }
                IconButton(onClick = onAddImageOverlay, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Image, contentDescription = "Add Overlay", tint = Color(0xFFEC4899))
                }

                Spacer(modifier = Modifier.width(4.dp))

                IconButton(
                    onClick = { zoomScale = (zoomScale - 0.5f).coerceAtLeast(1.0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Remove, contentDescription = "Zoom Out", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }

                Text(
                    text = "${"%.1f".format(zoomScale)}x",
                    color = CanvasEditorColors.AccentPurple,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                IconButton(
                    onClick = { zoomScale = (zoomScale + 0.5f).coerceAtMost(8.0f) },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Zoom In", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Base width calculation
        var baseTimelineWidthPx by remember { mutableFloatStateOf(1f) }

        Spacer(modifier = Modifier.height(4.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .onSizeChanged { baseTimelineWidthPx = it.width.toFloat().coerceAtLeast(1f) }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, zoom, _ ->
                        zoomScale = (zoomScale * zoom).coerceIn(1.0f, 8.0f)
                    }
                }
        ) {
            val effectiveWidthPx = baseTimelineWidthPx * zoomScale
            val extraEndPaddingPx = with(density) { 260.dp.toPx() }
            val totalScrollableWidthPx = effectiveWidthPx + extraEndPaddingPx
            val scrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Box(
                    modifier = Modifier.width(with(density) { totalScrollableWidthPx.toDp() })
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // --- TIME RULER HEADER (INSIDE SCROLLABLE AREA FOR 1-TO-1 SCRUBBING) ---
                        Box(
                            modifier = Modifier
                                .width(with(density) { totalScrollableWidthPx.toDp() })
                                .height(22.dp)
                                .pointerInput(totalDurationMs, effectiveWidthPx) {
                                    detectTapGestures { offset ->
                                        val clickedMs = ((offset.x / effectiveWidthPx) * totalDurationMs).toLong()
                                        onSeekTo(clickedMs.coerceIn(0L, totalDurationMs))
                                    }
                                }
                                .pointerInput(totalDurationMs, effectiveWidthPx) {
                                    detectDragGestures(
                                        onDragStart = { offset ->
                                            val seekMs = ((offset.x / effectiveWidthPx) * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)
                                            onSeekTo(seekMs)
                                        },
                                        onDrag = { change, _ ->
                                            change.consume()
                                            var seekMs = ((change.position.x / effectiveWidthPx) * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)
                                            val snapThresholdMs = (200L / zoomScale).toLong().coerceIn(80L, 300L)
                                            for (target in snapTargetsMs) {
                                                if (abs(seekMs - target) <= snapThresholdMs) {
                                                    seekMs = target
                                                    break
                                                }
                                            }
                                            onSeekTo(seekMs)
                                        }
                                    )
                                }
                        ) {
                            Row(
                                modifier = Modifier
                                    .width(with(density) { effectiveWidthPx.toDp() })
                                    .padding(horizontal = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                val steps = 5
                                for (i in 0..steps) {
                                    val markMs = (totalDurationMs * i) / steps
                                    Text(
                                        text = "${markMs / 1000}s",
                                        color = Color.Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        // ==================== VIDEO TRACK ====================
                        Row(
                            modifier = Modifier
                                .width(with(density) { effectiveWidthPx.toDp() })
                                .height(44.dp)
                                .background(Color(0xFF1E1E2E), RoundedCornerShape(8.dp)),
                            horizontalArrangement = Arrangement.Start
                        ) {
                            if (videoClips.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No Video Clips", color = Color.Gray, fontSize = 11.sp)
                                }
                            } else {
                                videoClips.forEachIndexed { index, clip ->
                                    val clipWidthDp = with(density) { ((clip.trimmedDurationMs.toFloat() / totalDurationMs) * effectiveWidthPx).toDp() }
                                    val isSelected = clip.id == selectedVideoClipId

                                    var filmstripFrames by remember(clip.id, clip.trimStartMs, clip.trimEndMs) {
                                        mutableStateOf<List<Bitmap>>(emptyList())
                                    }

                                    LaunchedEffect(clip.id, clip.trimStartMs, clip.trimEndMs) {
                                        ThumbnailProvider.getOrFetchThumbnails(context, clip, count = 5) { frames ->
                                            filmstripFrames = frames
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .width(clipWidthDp)
                                            .height(44.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isSelected) Color(0xFF3B3B70) else Color(0xFF28283E))
                                            .then(
                                                if (isSelected) Modifier.border(2.dp, Color(0xFF7C4DFF), RoundedCornerShape(6.dp))
                                                else Modifier.border(0.5.dp, Color.Gray.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                                            )
                                            .clickable { onSelectVideoClip(clip.id) }
                                    ) {
                                        if (filmstripFrames.isNotEmpty()) {
                                            Row(modifier = Modifier.fillMaxSize()) {
                                                filmstripFrames.forEach { frame ->
                                                    Image(
                                                        bitmap = frame.asImageBitmap(),
                                                        contentDescription = null,
                                                        contentScale = ContentScale.Crop,
                                                        modifier = Modifier
                                                            .weight(1f)
                                                            .fillMaxHeight()
                                                    )
                                                }
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .background(Color.Black.copy(alpha = 0.35f))
                                            )
                                        }

                                        Text(
                                            text = "🎬 ${clip.name} (${"%.1f".format(clip.speed)}x)",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            maxLines = 1,
                                            modifier = Modifier
                                                .align(Alignment.CenterStart)
                                                .padding(horizontal = 6.dp)
                                        )

                                        // Render Keyframe Diamond Markers
                                        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                                            val clipWidthDp = maxWidth
                                            clip.keyframes.forEach { kf ->
                                                val kfFrac = (kf.timeMs.toFloat() / clip.trimmedDurationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                                val kfXDp = clipWidthDp * kfFrac
                                                Box(
                                                    modifier = Modifier
                                                        .align(Alignment.CenterStart)
                                                        .offset(x = (kfXDp - 5.dp).coerceAtLeast(0.dp))
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(10.dp)
                                                            .graphicsLayer { rotationZ = 45f }
                                                            .background(Color.Yellow, RoundedCornerShape(1.dp))
                                                            .border(0.5.dp, Color.Black, RoundedCornerShape(1.dp))
                                                    )
                                                }
                                            }
                                        }

                                        // Transition Badge Icon
                                        if (index < videoClips.size - 1 || clip.transitionToNext != TransitionType.NONE) {
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.CenterEnd)
                                                    .padding(end = 4.dp)
                                                    .size(20.dp)
                                                    .background(
                                                        if (clip.transitionToNext != TransitionType.NONE) Color(0xFF7C4DFF) else Color(0xFF3A3A3A),
                                                        CircleShape
                                                    )
                                                    .clickable {
                                                        onSelectVideoClip(clip.id)
                                                        onSelectTransition?.invoke(clip.id)
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = when (clip.transitionToNext) {
                                                        TransitionType.CROSSFADE -> "🔀"
                                                        TransitionType.SLIDE_LEFT -> "⬅"
                                                        TransitionType.SLIDE_RIGHT -> "➡"
                                                        TransitionType.FADE_BLACK -> "⬛"
                                                        else -> "⧉"
                                                    },
                                                    fontSize = 10.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                // CapCut-style "+" Add Video Clip Tile
                                Box(
                                    modifier = Modifier
                                        .width(44.dp)
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CanvasEditorColors.CardDarkElevated)
                                        .clickable { onAddVideo() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Add Video Clip",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }

                        // ==================== AUDIO TRACK ====================
                        if (audioClips.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(38.dp)
                                    .background(Color(0xFF1E1A10), RoundedCornerShape(8.dp))
                            ) {
                                audioClips.forEach { audio ->
                                    val isSelected = audio.id == selectedAudioClipId
                                    var waveformPeaks by remember(audio.id, audio.trimStartMs, audio.trimEndMs) {
                                        mutableStateOf(audio.waveformData)
                                    }

                                    LaunchedEffect(audio.id, audio.trimStartMs, audio.trimEndMs) {
                                        AudioWaveformExtractor.extractWaveform(context, audio, sampleCount = 40) { peaks ->
                                            waveformPeaks = peaks
                                        }
                                    }

                                    DraggableTimelineBlock(
                                        id = audio.id,
                                        label = "🎵 ${audio.name}",
                                        startMs = audio.startTimeMs,
                                        durationMs = audio.trimmedDurationMs,
                                        totalDurationMs = totalDurationMs,
                                        trackWidthPx = effectiveWidthPx,
                                        snapTargetsMs = snapTargetsMs,
                                        height = 38.dp,
                                        color = Color(0xFF8C520B),
                                        selectedColor = Color(0xFFD38312),
                                        isSelected = isSelected,
                                        allowResize = false,
                                        onSelect = { onSelectAudioClip(audio.id) },
                                        onMove = { newStart -> onMoveAudioClip(audio.id, newStart) },
                                        onDelete = { onDeleteAudioClip(audio.id) },
                                        extraAction = {
                                            IconButton(
                                                onClick = { onSplitAudioAt(audio.id, currentTimeMs) },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(Icons.Default.ContentCut, contentDescription = "Split", tint = Color.White, modifier = Modifier.size(12.dp))
                                            }
                                        }
                                    ) {
                                        Canvas(modifier = Modifier.fillMaxSize()) {
                                            val barWidth = 3.dp.toPx()
                                            val gap = 2.dp.toPx()
                                            val count = (size.width / (barWidth + gap)).toInt().coerceAtLeast(1)

                                            val peaksToDraw = if (waveformPeaks.isNotEmpty()) waveformPeaks else audio.waveformData

                                            for (i in 0 until count) {
                                                val amp = if (peaksToDraw.isNotEmpty()) {
                                                    peaksToDraw[i % peaksToDraw.size]
                                                } else {
                                                    0.3f
                                                }
                                                val barHeight = size.height * amp * 0.75f
                                                val x = i * (barWidth + gap)
                                                val y = (size.height - barHeight) / 2f

                                                drawRect(
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    topLeft = Offset(x, y),
                                                    size = Size(barWidth, barHeight)
                                                )
                                            }

                                            audio.beatMarkersMs.forEach { beatMs ->
                                                val frac = (beatMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                                                val bx = frac * size.width
                                                drawRect(
                                                    color = Color.Yellow,
                                                    topLeft = Offset(bx - 1f, 0f),
                                                    size = Size(3f, size.height)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // ==================== IMAGE OVERLAY TRACK ====================
                        if (imageOverlays.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(Color(0xFF281C30), RoundedCornerShape(8.dp))
                            ) {
                                imageOverlays.forEach { overlay ->
                                    val isSelected = overlay.id == selectedImageOverlayId
                                    DraggableTimelineBlock(
                                        id = overlay.id,
                                        label = "🖼 ${overlay.name}",
                                        startMs = overlay.startTimeMs,
                                        durationMs = overlay.durationMs,
                                        totalDurationMs = totalDurationMs,
                                        trackWidthPx = effectiveWidthPx,
                                        snapTargetsMs = snapTargetsMs,
                                        height = 30.dp,
                                        color = Color(0xFF6B21A8),
                                        selectedColor = Color(0xFF9333EA),
                                        isSelected = isSelected,
                                        allowResize = true,
                                        onSelect = { onSelectImageOverlay(overlay.id) },
                                        onMove = { newStart -> onMoveImageOverlay(overlay.id, newStart) },
                                        onResize = { newDuration -> onResizeImageOverlay(overlay.id, newDuration) },
                                        onDelete = { onDeleteImageOverlay(overlay.id) }
                                    )
                                }
                            }
                        }

                        // ==================== TEXT OVERLAY TRACK ====================
                        if (textOverlays.isNotEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .background(Color(0xFF101E1A), RoundedCornerShape(8.dp))
                            ) {
                                textOverlays.forEach { overlay ->
                                    val isSelected = overlay.id == selectedTextOverlayId
                                    DraggableTimelineBlock(
                                        id = overlay.id,
                                        label = "💬 ${overlay.text}",
                                        startMs = overlay.startTimeMs,
                                        durationMs = overlay.durationMs,
                                        totalDurationMs = totalDurationMs,
                                        trackWidthPx = effectiveWidthPx,
                                        snapTargetsMs = snapTargetsMs,
                                        height = 30.dp,
                                        color = Color(0xFF165B37),
                                        selectedColor = Color(0xFF27AE60),
                                        isSelected = isSelected,
                                        allowResize = true,
                                        onSelect = { onSelectTextOverlay(overlay.id) },
                                        onMove = { newStart -> onMoveTextLayer(overlay.id, newStart) },
                                        onResize = { newDuration -> onResizeTextLayer(overlay.id, newDuration) },
                                        onDelete = { onDeleteTextLayer(overlay.id) }
                                    )
                                }
                            }
                        }
                    }

                    // --- WHITE PLAYHEAD NEEDLE WITH ABSOLUTE SCRUBBABLE HANDLE ---
                    val progressFrac = (currentTimeMs.toFloat() / totalDurationMs).coerceIn(0f, 1f)
                    val needleOffsetPx = progressFrac * effectiveWidthPx

                    val totalTrackHeight = 44.dp +
                            (if (audioClips.isNotEmpty()) 44.dp else 0.dp) +
                            (if (imageOverlays.isNotEmpty()) 36.dp else 0.dp) +
                            (if (textOverlays.isNotEmpty()) 36.dp else 0.dp)

                    Box(
                        modifier = Modifier
                            .offset { IntOffset((needleOffsetPx - 16f).toInt().coerceAtLeast(0), 0) }
                            .width(32.dp)
                            .height(totalTrackHeight + 16.dp)
                            .pointerInput(totalDurationMs, effectiveWidthPx) {
                                detectDragGestures(
                                    onDragStart = { offset ->
                                        val absoluteTouchX = offset.x + (needleOffsetPx - 16f)
                                        val seekMs = ((absoluteTouchX / effectiveWidthPx) * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)
                                        onSeekTo(seekMs)
                                    },
                                    onDrag = { change, _ ->
                                        change.consume()
                                        val absoluteTouchX = change.position.x + (needleOffsetPx - 16f)
                                        var seekMs = ((absoluteTouchX / effectiveWidthPx) * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)

                                        val snapThresholdMs = (200L / zoomScale).toLong().coerceIn(80L, 300L)
                                        for (target in snapTargetsMs) {
                                            if (abs(seekMs - target) <= snapThresholdMs) {
                                                seekMs = target
                                                break
                                            }
                                        }
                                        onSeekTo(seekMs)
                                    }
                                )
                            }
                    ) {
                        // Playhead Vertical Line
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .width(3.dp)
                                .fillMaxHeight()
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )

                        // Playhead Diamond Handle at Top
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .offset(y = (-2).dp)
                                .size(16.dp)
                                .graphicsLayer { rotationZ = 45f }
                                .background(Color.White, RoundedCornerShape(2.dp))
                                .border(1.dp, CanvasEditorColors.AccentPurple, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
        }
    }
}

// ============================================================
// IMAGE OVERLAY LAYER & PANELS
// ============================================================

@Composable
fun rememberImageBitmap(uri: Uri): ImageBitmap? {
    val context = LocalContext.current
    return remember(uri) {
        try {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream)?.asImageBitmap()
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Composable
fun ImageOverlayLayer(
    overlays: List<ImageOverlayItem>,
    selectedId: String?,
    currentTimeMs: Long,
    onSelectOverlay: (id: String) -> Unit,
    onOverlayMoved: (id: String, xFraction: Float, yFraction: Float) -> Unit,
    onRemoveOverlay: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(Offset(1f, 1f)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = Offset(it.width.toFloat().coerceAtLeast(1f), it.height.toFloat().coerceAtLeast(1f)) }
    ) {
        overlays.forEach { overlay ->
            val isVisible = currentTimeMs >= overlay.startTimeMs &&
                    currentTimeMs <= (overlay.startTimeMs + overlay.durationMs)

            if (isVisible) {
                var currentXFrac by remember(overlay.id, overlay.xFraction) { mutableStateOf(overlay.xFraction) }
                var currentYFrac by remember(overlay.id, overlay.yFraction) { mutableStateOf(overlay.yFraction) }

                val xPx = currentXFrac * boxSize.x
                val yPx = currentYFrac * boxSize.y
                val isSelected = overlay.id == selectedId

                var imageSize by remember { mutableStateOf(Offset(0f, 0f)) }
                val imageBitmap = rememberImageBitmap(overlay.uri)

                Box(
                    modifier = Modifier
                        .onSizeChanged { imageSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                        .offset {
                            IntOffset(
                                (xPx - imageSize.x / 2f).toInt(),
                                (yPx - imageSize.y / 2f).toInt()
                            )
                        }
                        .graphicsLayer {
                            rotationZ = overlay.rotationDegrees
                            scaleX = overlay.scale
                            scaleY = overlay.scale
                            alpha = overlay.opacity
                        }
                        .pointerInput(overlay.id, boxSize) {
                            detectDragGestures(
                                onDragStart = { onSelectOverlay(overlay.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentXFrac = (currentXFrac + dragAmount.x / boxSize.x).coerceIn(0.05f, 0.95f)
                                    currentYFrac = (currentYFrac + dragAmount.y / boxSize.y).coerceIn(0.05f, 0.95f)
                                    onOverlayMoved(overlay.id, currentXFrac, currentYFrac)
                                }
                            )
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, CanvasEditorColors.AccentPurple, RoundedCornerShape(8.dp))
                            } else Modifier
                        )
                        .padding(4.dp)
                ) {
                    if (imageBitmap != null) {
                        Image(
                            bitmap = imageBitmap,
                            contentDescription = overlay.name,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.size(100.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(100.dp)
                                .background(Color.DarkGray, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("🖼", fontSize = 24.sp)
                        }
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 8.dp, y = (-8).dp)
                                .size(22.dp)
                                .background(CanvasEditorColors.DeleteBadge, CircleShape)
                                .clickable { onRemoveOverlay(overlay.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete Overlay",
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ToolPanelContainer(
    title: String,
    onDelete: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(CanvasEditorColors.SurfaceDark, RoundedCornerShape(16.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(title, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CanvasEditorColors.DeleteBadge)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                }

                IconButton(onClick = onClose, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Default.Check, contentDescription = "Done", tint = CanvasEditorColors.AccentPurple)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        content()
    }
}

@Composable
fun OverlayPanel(
    overlays: List<ImageOverlayItem>,
    selectedId: String?,
    onSelectOverlay: (String) -> Unit,
    onPickImage: () -> Unit,
    onRemove: (String) -> Unit,
    onUpdateOverlay: (ImageOverlayItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedOverlay = overlays.find { it.id == selectedId } ?: overlays.lastOrNull()

    ToolPanelContainer(
        title = "PNG & Image Overlays",
        onDelete = if (selectedOverlay != null) { { onRemove(selectedOverlay.id) } } else null,
        onClose = onClose,
        modifier = modifier
    ) {
        Button(onClick = onPickImage, modifier = Modifier.fillMaxWidth().height(48.dp)) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Import Image / PNG Overlay", fontWeight = FontWeight.Bold)
        }

        if (overlays.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                overlays.forEach { overlay ->
                    val isSelected = overlay.id == selectedOverlay?.id
                    Box(
                        modifier = Modifier
                            .background(
                                if (isSelected) CanvasEditorColors.AccentPurple else CanvasEditorColors.CardDark,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectOverlay(overlay.id) }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = overlay.name,
                            color = Color.White,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            if (selectedOverlay != null) {
                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Editing: ${selectedOverlay.name}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        IconButton(onClick = { onRemove(selectedOverlay.id) }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = CanvasEditorColors.DeleteBadge)
                        }
                    }

                    Text("Scale / Size: ${"%.1f".format(selectedOverlay.scale)}x", color = Color.LightGray, fontSize = 12.sp)
                    Slider(
                        value = selectedOverlay.scale,
                        onValueChange = { onUpdateOverlay(selectedOverlay.copy(scale = it)) },
                        valueRange = 0.2f..3.0f
                    )

                    Text("Position X: ${"%.2f".format(selectedOverlay.xFraction)}", color = Color.LightGray, fontSize = 12.sp)
                    Slider(
                        value = selectedOverlay.xFraction,
                        onValueChange = { onUpdateOverlay(selectedOverlay.copy(xFraction = it)) },
                        valueRange = 0f..1f
                    )

                    Text("Position Y: ${"%.2f".format(selectedOverlay.yFraction)}", color = Color.LightGray, fontSize = 12.sp)
                    Slider(
                        value = selectedOverlay.yFraction,
                        onValueChange = { onUpdateOverlay(selectedOverlay.copy(yFraction = it)) },
                        valueRange = 0f..1f
                    )

                    Text("Rotation: ${selectedOverlay.rotationDegrees.toInt()}°", color = Color.LightGray, fontSize = 12.sp)
                    Slider(
                        value = selectedOverlay.rotationDegrees,
                        onValueChange = { onUpdateOverlay(selectedOverlay.copy(rotationDegrees = it)) },
                        valueRange = 0f..360f
                    )

                    Text("Opacity: ${(selectedOverlay.opacity * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                    Slider(
                        value = selectedOverlay.opacity,
                        onValueChange = { onUpdateOverlay(selectedOverlay.copy(opacity = it)) },
                        valueRange = 0f..1f
                    )
                }
            }
        }
    }
}

// ============================================================
// TRANSFORM PANEL (Zoom / Scale, Position X/Y, Rotation & Opacity)
// ============================================================

@Composable
fun TransformPanel(
    selectedClip: VideoClip?,
    canvasAspectRatio: String = "9:16",
    onTransformChanged: (scale: Float, translateX: Float, translateY: Float, rotation: Float, opacity: Float, isMirrored: Boolean) -> Unit,
    onDeleteClip: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Zoom, Position, Rotation & Opacity", onDelete = onDeleteClip, onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to use Transform tools.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val fitScale = computeFitToCanvasScale(selectedClip.nativeWidthPx, selectedClip.nativeHeightPx, canvasAspectRatio)
            val alreadyFit = abs(selectedClip.scale - fitScale) < 0.05f

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (fitScale > 1.01f) {
                    OutlinedButton(
                        onClick = {
                            val target = if (alreadyFit) 1f else fitScale
                            onTransformChanged(target, selectedClip.translateXFraction, selectedClip.translateYFraction, selectedClip.rotationDegrees, selectedClip.opacity, selectedClip.isMirrored)
                        },
                        modifier = Modifier.weight(1f).height(42.dp)
                    ) {
                        Icon(Icons.Default.ZoomOutMap, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (alreadyFit) "Reset Zoom" else "Fit to Canvas")
                    }
                }

                FilterChip(
                    selected = selectedClip.isMirrored,
                    onClick = {
                        onTransformChanged(selectedClip.scale, selectedClip.translateXFraction, selectedClip.translateYFraction, selectedClip.rotationDegrees, selectedClip.opacity, !selectedClip.isMirrored)
                    },
                    label = { Text("Mirror Horizontal") }
                )
            }

            Text("Zoom / Scale: ${(selectedClip.scale * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = selectedClip.scale,
                onValueChange = { onTransformChanged(it, selectedClip.translateXFraction, selectedClip.translateYFraction, selectedClip.rotationDegrees, selectedClip.opacity, selectedClip.isMirrored) },
                valueRange = 1.0f..3.0f
            )

            Text("Position X Offset: ${"%.2f".format(selectedClip.translateXFraction)}", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = selectedClip.translateXFraction,
                onValueChange = { onTransformChanged(selectedClip.scale, it, selectedClip.translateYFraction, selectedClip.rotationDegrees, selectedClip.opacity, selectedClip.isMirrored) },
                valueRange = -0.5f..0.5f
            )

            Text("Position Y Offset: ${"%.2f".format(selectedClip.translateYFraction)}", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = selectedClip.translateYFraction,
                onValueChange = { onTransformChanged(selectedClip.scale, selectedClip.translateXFraction, it, selectedClip.rotationDegrees, selectedClip.opacity, selectedClip.isMirrored) },
                valueRange = -0.5f..0.5f
            )

            Text("Rotation Angle: ${selectedClip.rotationDegrees.toInt()}°", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = selectedClip.rotationDegrees,
                onValueChange = { onTransformChanged(selectedClip.scale, selectedClip.translateXFraction, selectedClip.translateYFraction, it, selectedClip.opacity, selectedClip.isMirrored) },
                valueRange = 0f..360f
            )

            Text("Opacity: ${(selectedClip.opacity * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = selectedClip.opacity,
                onValueChange = { onTransformChanged(selectedClip.scale, selectedClip.translateXFraction, selectedClip.translateYFraction, selectedClip.rotationDegrees, it, selectedClip.isMirrored) },
                valueRange = 0f..1.0f
            )
        }
    }
}

@Composable
fun TextOverlayLayer(
    overlays: List<TextOverlayItem>,
    selectedId: String?,
    currentTimeMs: Long,
    onSelectOverlay: (id: String) -> Unit,
    onOverlayMoved: (id: String, xFraction: Float, yFraction: Float) -> Unit,
    onRemoveOverlay: (id: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var boxSize by remember { mutableStateOf(Offset(1f, 1f)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { boxSize = Offset(it.width.toFloat().coerceAtLeast(1f), it.height.toFloat().coerceAtLeast(1f)) }
    ) {
        overlays.forEach { overlay ->
            val isVisible = currentTimeMs >= overlay.startTimeMs &&
                    currentTimeMs <= (overlay.startTimeMs + overlay.durationMs)

            if (isVisible) {
                var currentXFrac by remember(overlay.id, overlay.xFraction) { mutableFloatStateOf(overlay.xFraction) }
                var currentYFrac by remember(overlay.id, overlay.yFraction) { mutableFloatStateOf(overlay.yFraction) }

                val xPx = currentXFrac * boxSize.x
                val yPx = currentYFrac * boxSize.y
                val isSelected = overlay.id == selectedId

                var textSize by remember { mutableStateOf(Offset(0f, 0f)) }

                Box(
                    modifier = Modifier
                        .onSizeChanged { textSize = Offset(it.width.toFloat(), it.height.toFloat()) }
                        .offset {
                            IntOffset(
                                (xPx - textSize.x / 2f).toInt(),
                                (yPx - textSize.y / 2f).toInt()
                            )
                        }
                        .graphicsLayer {
                            rotationZ = overlay.rotationDegrees
                            scaleX = overlay.scale
                            scaleY = overlay.scale
                            alpha = overlay.opacity
                        }
                        .pointerInput(overlay.id, boxSize) {
                            detectDragGestures(
                                onDragStart = { onSelectOverlay(overlay.id) },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    currentXFrac = (currentXFrac + dragAmount.x / boxSize.x).coerceIn(0.05f, 0.95f)
                                    currentYFrac = (currentYFrac + dragAmount.y / boxSize.y).coerceIn(0.05f, 0.95f)
                                    onOverlayMoved(overlay.id, currentXFrac, currentYFrac)
                                }
                            )
                        }
                        .then(
                            if (isSelected) {
                                Modifier.border(2.dp, CanvasEditorColors.AccentPurple, RoundedCornerShape(8.dp))
                            } else Modifier
                        )
                        .background(overlay.backgroundColor, RoundedCornerShape(6.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = overlay.text,
                        color = overlay.color,
                        fontSize = overlay.fontSizeSp.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    currentSettings: ExportSettings,
    onDismiss: () -> Unit,
    onSave: (ExportSettings) -> Unit
) {
    var selectedRes by remember { mutableStateOf(currentSettings.resolution) }
    var selectedFps by remember { mutableStateOf(currentSettings.fps) }
    var selectedAspect by remember { mutableStateOf(currentSettings.aspectRatio) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Canvas & Export Settings") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Aspect Ratio:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("9:16", "16:9", "1:1").forEach { aspect ->
                        FilterChip(
                            selected = selectedAspect == aspect,
                            onClick = { selectedAspect = aspect },
                            label = { Text(aspect) }
                        )
                    }
                }

                Text("Resolution:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("720p", "1080p", "4K").forEach { res ->
                        FilterChip(
                            selected = selectedRes == res,
                            onClick = { selectedRes = res },
                            label = { Text(res) }
                        )
                    }
                }

                Text("Frame Rate (FPS):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(30, 60).forEach { fps ->
                        FilterChip(
                            selected = selectedFps == fps,
                            onClick = { selectedFps = fps },
                            label = { Text("${fps}fps") }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                onSave(ExportSettings(resolution = selectedRes, fps = selectedFps, aspectRatio = selectedAspect))
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ClipsPanel(
    videoClips: List<VideoClip>,
    selectedClipId: String?,
    onSelectClip: (String) -> Unit,
    onMoveClipUp: (Int) -> Unit,
    onMoveClipDown: (Int) -> Unit,
    onRemoveClip: (String) -> Unit,
    onAddVideo: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Video Sequence Clips (${videoClips.size})", onClose = onClose, modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Button(
                onClick = onAddVideo,
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Video Clips")
            }

            videoClips.forEachIndexed { index, clip ->
                val isSelected = clip.id == selectedClipId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) CanvasEditorColors.AccentPurpleSoft else CanvasEditorColors.CardDark)
                        .border(
                            1.dp,
                            if (isSelected) CanvasEditorColors.AccentPurple else Color.Transparent,
                            RoundedCornerShape(10.dp)
                        )
                        .clickable { onSelectClip(clip.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(clip.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(
                            "${formatMsDetailed(clip.trimmedDurationMs)} • ${"%.1f".format(clip.speed)}x",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onMoveClipUp(index) },
                            enabled = index > 0,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move Up", tint = Color.White)
                        }

                        IconButton(
                            onClick = { onMoveClipDown(index) },
                            enabled = index < videoClips.size - 1,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move Down", tint = Color.White)
                        }

                        IconButton(
                            onClick = { onRemoveClip(clip.id) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = CanvasEditorColors.DeleteBadge)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimPanel(
    selectedClip: VideoClip?,
    onTrimChanged: (startMs: Long, endMs: Long) -> Unit,
    onDeleteClip: (() -> Unit)? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Trim Clip Start / End", onDelete = onDeleteClip, onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to trim.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        val maxDuration = selectedClip.sourceDurationMs.toFloat().coerceAtLeast(100f)
        var sliderRange by remember(selectedClip.id, selectedClip.trimStartMs, selectedClip.trimEndMs) {
            mutableStateOf(selectedClip.trimStartMs.toFloat()..selectedClip.trimEndMs.toFloat())
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Selected Clip: ${selectedClip.name}", color = Color.White, fontWeight = FontWeight.Bold)
            Text(
                "Trim: ${formatMsDetailed(sliderRange.start.toLong())} - ${formatMsDetailed(sliderRange.endInclusive.toLong())} (Duration: ${formatMsDetailed((sliderRange.endInclusive - sliderRange.start).toLong())})",
                color = Color.LightGray,
                fontSize = 12.sp
            )

            RangeSlider(
                value = sliderRange,
                onValueChange = { range ->
                    sliderRange = range
                    onTrimChanged(range.start.toLong(), range.endInclusive.toLong())
                },
                valueRange = 0f..maxDuration,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun SplitPanel(
    selectedClip: VideoClip?,
    selectedAudio: AudioClip?,
    selectedText: TextOverlayItem? = null,
    selectedOverlay: ImageOverlayItem? = null,
    currentPositionMs: Long,
    clipStartInProjectMs: Long,
    onSplitVideo: () -> Unit,
    onSplitAudio: () -> Unit,
    onSplitText: () -> Unit = {},
    onSplitOverlay: () -> Unit = {},
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Split Track at Playhead", onClose = onClose, modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Current Playhead: ${formatMsDetailed(currentPositionMs)}", color = Color.White, fontWeight = FontWeight.Bold)

            if (selectedClip != null) {
                Button(
                    onClick = onSplitVideo,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.ContentCut, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Split Video Clip (${selectedClip.name})")
                }
            }

            if (selectedText != null) {
                Button(
                    onClick = onSplitText,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF165B37)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.TextFields, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Split Text Layer (\"${selectedText.text}\")")
                }
            }

            if (selectedOverlay != null) {
                Button(
                    onClick = onSplitOverlay,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B21A8)),
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Split Overlay Layer (${selectedOverlay.name})")
                }
            }

            if (selectedAudio != null) {
                OutlinedButton(
                    onClick = onSplitAudio,
                    modifier = Modifier.fillMaxWidth().height(42.dp)
                ) {
                    Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Split Audio Track (${selectedAudio.name})")
                }
            }

            if (selectedClip == null && selectedText == null && selectedOverlay == null && selectedAudio == null) {
                Text("Select a video clip, text layer, or overlay to split.", color = Color.Gray, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun TextPanel(
    overlays: List<TextOverlayItem>,
    selectedId: String?,
    onSelectOverlay: (String) -> Unit,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
    onUpdateOverlay: (TextOverlayItem) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var newTextString by remember { mutableStateOf("") }
    val selectedItem = overlays.find { it.id == selectedId } ?: overlays.lastOrNull()

    ToolPanelContainer(title = "Text Overlay Tools", onClose = onClose, modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newTextString,
                    onValueChange = { newTextString = it },
                    label = { Text("New Caption Text") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        if (newTextString.isNotBlank()) {
                            onAdd(newTextString)
                            newTextString = ""
                        }
                    },
                    enabled = newTextString.isNotBlank()
                ) {
                    Text("Add")
                }
            }

            if (selectedItem != null) {
                Text("Editing: ${selectedItem.text}", color = Color.White, fontWeight = FontWeight.Bold)

                Text("Font Size: ${selectedItem.fontSizeSp.toInt()} sp", color = Color.LightGray, fontSize = 12.sp)
                Slider(
                    value = selectedItem.fontSizeSp,
                    onValueChange = { onUpdateOverlay(selectedItem.copy(fontSizeSp = it)) },
                    valueRange = 14f..60f
                )

                Text("Rotation: ${selectedItem.rotationDegrees.toInt()}°", color = Color.LightGray, fontSize = 12.sp)
                Slider(
                    value = selectedItem.rotationDegrees,
                    onValueChange = { onUpdateOverlay(selectedItem.copy(rotationDegrees = it)) },
                    valueRange = 0f..360f
                )

                Text("Opacity: ${(selectedItem.opacity * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                Slider(
                    value = selectedItem.opacity,
                    onValueChange = { onUpdateOverlay(selectedItem.copy(opacity = it)) },
                    valueRange = 0f..1f
                )

                IconButton(
                    onClick = { onRemove(selectedItem.id) },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete Overlay", tint = CanvasEditorColors.DeleteBadge)
                }
            }
        }
    }
}

@Composable
fun AudioPanel(
    audioClips: List<AudioClip>,
    selectedAudioId: String?,
    muteOriginal: Boolean,
    currentTimeMs: Long,
    onPickAudio: () -> Unit,
    onRemoveAudio: (String) -> Unit,
    onUpdateAudio: (AudioClip) -> Unit,
    onMuteToggle: (Boolean) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedAudio = audioClips.find { it.id == selectedAudioId } ?: audioClips.firstOrNull()

    ToolPanelContainer(title = "Audio Tracks & Mixing", onClose = onClose, modifier = modifier) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 280.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mute Original Video Audio", color = Color.White, fontSize = 13.sp)
                Switch(checked = muteOriginal, onCheckedChange = onMuteToggle)
            }

            Button(
                onClick = onPickAudio,
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.MusicNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Import Music / Audio File")
            }

            if (selectedAudio != null) {
                Text("Track: ${selectedAudio.name}", color = Color.White, fontWeight = FontWeight.Bold)

                Text("Volume: ${(selectedAudio.volume * 100).toInt()}%", color = Color.LightGray, fontSize = 12.sp)
                Slider(
                    value = selectedAudio.volume,
                    onValueChange = { onUpdateAudio(selectedAudio.copy(volume = it)) },
                    valueRange = 0f..2f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = selectedAudio.isMuted,
                        onClick = { onUpdateAudio(selectedAudio.copy(isMuted = !selectedAudio.isMuted)) },
                        label = { Text(if (selectedAudio.isMuted) "Unmute Track" else "Mute Track") }
                    )

                    IconButton(onClick = { onRemoveAudio(selectedAudio.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Track", tint = CanvasEditorColors.DeleteBadge)
                    }
                }
            }
        }
    }
}

@Composable
fun SpeedPanel(
    selectedClip: VideoClip?,
    onSpeedChanged: (Float) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Clip Speed Control", onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to adjust speed.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Current Speed: ${"%.2f".format(selectedClip.speed)}x", color = Color.White, fontWeight = FontWeight.Bold)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.25f, 0.5f, 1.0f, 1.5f, 2.0f, 3.0f).forEach { speed ->
                    FilterChip(
                        selected = abs(selectedClip.speed - speed) < 0.05f,
                        onClick = { onSpeedChanged(speed) },
                        label = { Text("${speed}x") }
                    )
                }
            }

            Slider(
                value = selectedClip.speed,
                onValueChange = onSpeedChanged,
                valueRange = 0.2f..4.0f
            )
        }
    }
}

@Composable
fun TransitionsPanel(
    selectedClip: VideoClip?,
    onTransitionChanged: (TransitionType, Long) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Transition to Next Clip", onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to set transition.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        var durationMs by remember(selectedClip.id) { mutableFloatStateOf(selectedClip.transitionDurationMs.toFloat()) }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Text("Select Transition Style:", color = Color.White, fontWeight = FontWeight.Bold)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TransitionType.entries.forEach { type ->
                    FilterChip(
                        selected = selectedClip.transitionToNext == type,
                        onClick = { onTransitionChanged(type, durationMs.toLong()) },
                        label = { Text(type.name.replace("_", " ")) }
                    )
                }
            }

            Text("Transition Duration: ${durationMs.toInt()} ms", color = Color.LightGray, fontSize = 12.sp)
            Slider(
                value = durationMs,
                onValueChange = {
                    durationMs = it
                    onTransitionChanged(selectedClip.transitionToNext, it.toLong())
                },
                valueRange = 200f..2000f
            )
        }
    }
}

@Composable
fun FiltersPanel(
    selectedClip: VideoClip?,
    onFilterChanged: (ColorFilterPreset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Color Filters & LUT Grade", onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to apply filters.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ColorFilterPreset.entries.forEach { preset ->
                    FilterChip(
                        selected = selectedClip.filterPreset == preset,
                        onClick = { onFilterChanged(preset) },
                        label = { Text(preset.name.replace("_", " ")) }
                    )
                }
            }
        }
    }
}

@Composable
fun KeyframesPanel(
    selectedClip: VideoClip?,
    currentTimeMs: Long,
    clipStartInProjectMs: Long,
    onAddKeyframe: (TransformKeyframe) -> Unit,
    onRemoveKeyframe: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ToolPanelContainer(title = "Transform Keyframe Animation", onClose = onClose, modifier = modifier) {
        if (selectedClip == null) {
            Text("Select a video clip on the timeline to manage keyframes.", color = Color.LightGray)
            return@ToolPanelContainer
        }

        val clipTimeMs = (currentTimeMs - clipStartInProjectMs).coerceIn(0L, selectedClip.trimmedDurationMs)

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 260.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Button(
                onClick = {
                    val kf = TransformKeyframe(
                        timeMs = clipTimeMs,
                        scale = selectedClip.scale,
                        translateXFraction = selectedClip.translateXFraction,
                        translateYFraction = selectedClip.translateYFraction,
                        rotationDegrees = selectedClip.rotationDegrees,
                        opacity = selectedClip.opacity
                    )
                    onAddKeyframe(kf)
                },
                modifier = Modifier.fillMaxWidth().height(42.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Add Keyframe at ${formatMsDetailed(clipTimeMs)}")
            }

            Text("Keyframes on clip (${selectedClip.keyframes.size}):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

            selectedClip.keyframes.forEach { kf ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(CanvasEditorColors.CardDark)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Time: ${formatMsDetailed(kf.timeMs)} • Scale: ${"%.1f".format(kf.scale)}x",
                        color = Color.White,
                        fontSize = 12.sp
                    )

                    IconButton(onClick = { onRemoveKeyframe(kf.id) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete Keyframe", tint = CanvasEditorColors.DeleteBadge)
                    }
                }
            }
        }
    }
}