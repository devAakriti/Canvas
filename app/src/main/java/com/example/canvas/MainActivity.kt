package com.example.canvas

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Transform
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.canvas.ui.theme.CanvasEditorColors
import com.example.canvas.ui.theme.CanvasTheme
import kotlinx.coroutines.delay
import java.util.UUID
import kotlin.math.abs

/**
 * ViewModel managing active project draft, persistent internal storage auto-saving, and My Projects screen state.
 */
class ProjectViewModel : ViewModel() {
    var activeDraft by mutableStateOf<SavedProjectDraft?>(null)
        private set

    var savedDrafts by mutableStateOf<List<SavedProjectDraft>>(emptyList())
        private set

    var isEditingProject by mutableStateOf(false)
        private set

    val project: CanvasProject
        get() = activeDraft?.project ?: CanvasProject()

    private var undoStack = listOf<CanvasProject>()
    private var redoStack = listOf<CanvasProject>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun loadDrafts(context: Context) {
        savedDrafts = ProjectStorageManager.loadAllDrafts(context)
    }

    fun openDraft(draft: SavedProjectDraft) {
        activeDraft = draft
        undoStack = emptyList()
        redoStack = emptyList()
        isEditingProject = true
    }

    fun createNewProject(context: Context) {
        val newDraft = SavedProjectDraft(
            title = "Project ${savedDrafts.size + 1}",
            project = CanvasProject()
        )
        activeDraft = newDraft
        undoStack = emptyList()
        redoStack = emptyList()
        isEditingProject = true
        ProjectStorageManager.saveDraft(context, newDraft)
        loadDrafts(context)
    }

    fun closeEditor(context: Context) {
        if (activeDraft != null) {
            ProjectStorageManager.saveDraft(context, activeDraft!!)
        }
        isEditingProject = false
        loadDrafts(context)
    }

    fun deleteDraft(context: Context, id: String) {
        ProjectStorageManager.deleteDraft(context, id)
        if (activeDraft?.id == id) {
            activeDraft = null
            isEditingProject = false
        }
        loadDrafts(context)
    }

    fun updateProject(context: Context, transform: (CanvasProject) -> CanvasProject) {
        val currentProj = project
        undoStack = undoStack + currentProj
        redoStack = emptyList()

        val updatedProj = transform(currentProj)
        val updatedDraft = (activeDraft ?: SavedProjectDraft()).copy(
            lastModifiedMs = System.currentTimeMillis(),
            project = updatedProj
        )

        activeDraft = updatedDraft
        ProjectStorageManager.saveDraft(context, updatedDraft)
    }

    fun undo(context: Context) {
        if (undoStack.isNotEmpty()) {
            val prev = undoStack.last()
            undoStack = undoStack.dropLast(1)
            redoStack = redoStack + project

            val updatedDraft = (activeDraft ?: SavedProjectDraft()).copy(
                lastModifiedMs = System.currentTimeMillis(),
                project = prev
            )
            activeDraft = updatedDraft
            ProjectStorageManager.saveDraft(context, updatedDraft)
        }
    }

    fun redo(context: Context) {
        if (redoStack.isNotEmpty()) {
            val next = redoStack.last()
            redoStack = redoStack.dropLast(1)
            undoStack = undoStack + project

            val updatedDraft = (activeDraft ?: SavedProjectDraft()).copy(
                lastModifiedMs = System.currentTimeMillis(),
                project = next
            )
            activeDraft = updatedDraft
            ProjectStorageManager.saveDraft(context, updatedDraft)
        }
    }
}

@UnstableApi
class MainActivity : ComponentActivity() {

    private val viewModel: ProjectViewModel by viewModels()

    // Multi-video picker: allows importing multiple video clips at once from gallery
    private val videoPicker =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                val newClips = uris.mapIndexed { index, uri ->
                    val name = queryDisplayName(uri) ?: "Video ${viewModel.project.videoClips.size + index + 1}"
                    val durationMs = VideoExportManager.getVideoDurationMs(this, uri)
                    val (nativeW, nativeH) = VideoExportManager.getVideoDimensions(this, uri)
                    VideoClip(
                        uri = uri,
                        name = name,
                        sourceDurationMs = durationMs,
                        nativeWidthPx = nativeW,
                        nativeHeightPx = nativeH
                    )
                }
                viewModel.updateProject(this) { p ->
                    p.copy(
                        videoClips = p.videoClips + newClips,
                        selectedLayerId = newClips.last().id
                    )
                }
            }
        }

    private val audioPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val name = queryDisplayName(it) ?: "Audio ${viewModel.project.audioClips.size + 1}"
                val durationMs = VideoExportManager.getVideoDurationMs(this, it)
                val videoProjectDuration = viewModel.project.totalDurationMs
                val defaultTrimEnd = if (videoProjectDuration > 0L) videoProjectDuration.coerceAtMost(durationMs) else durationMs

                val newAudio = AudioClip(
                    uri = it,
                    name = name,
                    sourceDurationMs = durationMs,
                    trimStartMs = 0L,
                    trimEndMs = defaultTrimEnd
                )
                viewModel.updateProject(this) { p ->
                    p.copy(
                        audioClips = p.audioClips + newAudio,
                        selectedLayerId = newAudio.id
                    )
                }
            }
        }

    private val imagePicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                val name = queryDisplayName(it) ?: "Overlay ${viewModel.project.imageOverlays.size + 1}"
                val newOverlay = ImageOverlayItem(
                    uri = it,
                    name = name,
                    startTimeMs = viewModel.project.playheadPositionMs,
                    durationMs = 10000L
                )
                viewModel.updateProject(this) { p ->
                    p.copy(
                        imageOverlays = p.imageOverlays + newOverlay,
                        selectedLayerId = newOverlay.id
                    )
                }
            }
        }

    private fun queryDisplayName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (nameIndex >= 0 && cursor.moveToFirst()) cursor.getString(nameIndex) else null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        viewModel.loadDrafts(this)

        setContent {
            CanvasTheme {
                if (viewModel.isEditingProject) {
                    CanvasApp(
                        project = viewModel.project,
                        canUndo = viewModel.canUndo,
                        canRedo = viewModel.canRedo,
                        onUndo = { viewModel.undo(this) },
                        onRedo = { viewModel.redo(this) },
                        onUpdateProject = { transform -> viewModel.updateProject(this, transform) },
                        onOpenDraftsScreen = { viewModel.closeEditor(this) },
                        onAddVideo = { videoPicker.launch("video/*") },
                        onAddAudio = { audioPicker.launch("audio/*") },
                        onAddImageOverlay = { imagePicker.launch("image/*") }
                    )
                } else {
                    ProjectsScreen(
                        drafts = viewModel.savedDrafts,
                        onSelectDraft = { draft -> viewModel.openDraft(draft) },
                        onCreateNewProject = { viewModel.createNewProject(this) },
                        onDeleteDraft = { id -> viewModel.deleteDraft(this, id) }
                    )
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun CanvasApp(
    project: CanvasProject,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onUpdateProject: ((CanvasProject) -> CanvasProject) -> Unit,
    onOpenDraftsScreen: () -> Unit,
    onAddVideo: () -> Unit,
    onAddAudio: () -> Unit,
    onAddImageOverlay: () -> Unit
) {
    val context = LocalContext.current

    var activeTool by remember { mutableStateOf(EditorTool.NONE) }
    var isPlaying by remember { mutableStateOf(true) }
    var seekToMs by remember { mutableStateOf<Long?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }
    var exportState by remember { mutableStateOf(ExportState()) }

    val selectedVideoClip = project.selectedVideoClip
    val selectedAudioClip = project.selectedAudioClip
    val selectedTextLayer = project.selectedTextLayer
    val selectedImageOverlay = project.selectedImageOverlay

    var selectedClipStartInProjectMs = 0L
    if (selectedVideoClip != null) {
        for (clip in project.videoClips) {
            if (clip.id == selectedVideoClip.id) break
            selectedClipStartInProjectMs += clip.trimmedDurationMs
        }
    }

    if (showSettingsDialog) {
        SettingsDialog(
            currentSettings = project.canvasSettings,
            onDismiss = { showSettingsDialog = false },
            onSave = { newSettings ->
                onUpdateProject { p -> p.copy(canvasSettings = newSettings) }
                showSettingsDialog = false
            }
        )
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = CanvasEditorColors.BackgroundDark,
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CanvasEditorColors.SurfaceDark)
                    .navigationBarsPadding()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EditorToolButton(
                    icon = Icons.Default.Movie,
                    label = "Clips",
                    selected = activeTool == EditorTool.CLIPS,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.CLIPS) }
                )
                EditorToolButton(
                    icon = Icons.Default.ContentCut,
                    label = "Trim",
                    selected = activeTool == EditorTool.TRIM,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.TRIM) }
                )
                EditorToolButton(
                    icon = Icons.Default.ContentCut,
                    label = "Split",
                    selected = activeTool == EditorTool.SPLIT,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.SPLIT) }
                )
                EditorToolButton(
                    icon = Icons.Default.Image,
                    label = "Overlay",
                    selected = activeTool == EditorTool.OVERLAY,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.OVERLAY) }
                )
                EditorToolButton(
                    icon = Icons.Default.Speed,
                    label = "Speed",
                    selected = activeTool == EditorTool.SPEED,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.SPEED) }
                )
                EditorToolButton(
                    icon = Icons.Default.Transform,
                    label = "Transitions",
                    selected = activeTool == EditorTool.TRANSITIONS,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.TRANSITIONS) }
                )
                EditorToolButton(
                    icon = Icons.Default.ColorLens,
                    label = "Filters",
                    selected = activeTool == EditorTool.FILTERS,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.FILTERS) }
                )
                EditorToolButton(
                    icon = Icons.Default.Animation,
                    label = "Keyframes",
                    selected = activeTool == EditorTool.KEYFRAMES,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.KEYFRAMES) }
                )
                EditorToolButton(
                    icon = Icons.Default.Transform,
                    label = "Transform",
                    selected = activeTool == EditorTool.TRANSFORM,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.TRANSFORM) }
                )
                EditorToolButton(
                    icon = Icons.Default.TextFields,
                    label = "Text",
                    selected = activeTool == EditorTool.TEXT,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.TEXT) }
                )
                EditorToolButton(
                    icon = Icons.Default.MusicNote,
                    label = "Audio",
                    selected = activeTool == EditorTool.AUDIO,
                    onClick = { activeTool = toggleTool(activeTool, EditorTool.AUDIO) }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(CanvasEditorColors.BackgroundDark)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.SpaceBetween
        ) {

            // TOP BAR
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onOpenDraftsScreen, modifier = Modifier.size(48.dp)) {
                        Icon(Icons.Default.Folder, contentDescription = "My Projects", tint = CanvasEditorColors.TextPrimary)
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Canvas",
                        color = CanvasEditorColors.TextPrimary,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onUndo, enabled = canUndo, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Undo,
                            contentDescription = "Undo",
                            tint = if (canUndo) CanvasEditorColors.TextPrimary else CanvasEditorColors.TextSecondary.copy(alpha = 0.4f)
                        )
                    }

                    IconButton(onClick = onRedo, enabled = canRedo, modifier = Modifier.size(48.dp)) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Redo,
                            contentDescription = "Redo",
                            tint = if (canRedo) CanvasEditorColors.TextPrimary else CanvasEditorColors.TextSecondary.copy(alpha = 0.4f)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    if (project.videoClips.isNotEmpty()) {
                        IconTextButton(
                            icon = Icons.Default.FileDownload,
                            label = "Export",
                            enabled = !exportState.isExporting,
                            onClick = {
                                exportState = ExportState(isExporting = true)
                                VideoExportManager.export(
                                    context = context,
                                    videoClips = project.videoClips,
                                    textOverlays = project.textLayers,
                                    imageOverlays = project.imageOverlays,
                                    audioClips = project.audioClips,
                                    muteOriginalAudio = project.muteOriginalAudio,
                                    exportSettings = project.canvasSettings,
                                    onProgress = { p ->
                                        exportState = exportState.copy(progress = p)
                                    },
                                    onComplete = { uri ->
                                        exportState = ExportState(
                                            resultMessage = "Saved to Movies/Canvas",
                                            exportedUri = uri,
                                            isError = false
                                        )
                                    },
                                    onError = { e ->
                                        exportState = ExportState(
                                            resultMessage = e.message ?: "Export failed",
                                            isError = true
                                        )
                                    }
                                )
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(onClick = { showSettingsDialog = true }, modifier = Modifier.size(48.dp)) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings", tint = CanvasEditorColors.TextPrimary)
                    }
                }
            }

            // Export progress & completion bar
            if (exportState.isExporting) {
                LinearProgressIndicator(
                    progress = { exportState.progress },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                    color = CanvasEditorColors.AccentPurple
                )
                Spacer(modifier = Modifier.height(4.dp))
            } else if (exportState.resultMessage != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = exportState.resultMessage!!,
                        color = if (exportState.isError) CanvasEditorColors.DeleteBadge else Color(0xFF10B981),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f)
                    )

                    if (exportState.exportedUri != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(
                                onClick = { openExportedVideo(context, exportState.exportedUri!!) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Open", fontSize = 11.sp)
                            }

                            Button(
                                onClick = { shareExportedVideo(context, exportState.exportedUri!!) },
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Share", modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }

            // =========================
            // VIDEO PREVIEW WITH LIVE KEYFRAME ANIMATIONS & TEXT OVERLAYS
            // =========================
            val aspectModifier = when (project.canvasSettings.aspectRatio) {
                "16:9" -> Modifier.aspectRatio(16f / 9f)
                "9:16" -> Modifier.aspectRatio(9f / 16f)
                "1:1" -> Modifier.aspectRatio(1f)
                else -> Modifier.aspectRatio(9f / 16f)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .shadow(8.dp, RoundedCornerShape(16.dp))
                    .border(1.5.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .background(CanvasEditorColors.SurfaceDark, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (project.videoClips.isEmpty()) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text(
                            text = "No video clips in project",
                            color = CanvasEditorColors.TextSecondary,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(onClick = onAddVideo, modifier = Modifier.height(48.dp)) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Add video")
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("Import Video Clips")
                        }
                    }
                } else {
                    Box(modifier = aspectModifier, contentAlignment = Alignment.Center) {
                        // Determine currently playing video clip based on playhead position
                        var currentPlayingClip: VideoClip? = null
                        var currentClipStartMs = 0L
                        var accMs = 0L

                        for (clip in project.videoClips) {
                            val duration = clip.trimmedDurationMs
                            if (project.playheadPositionMs in accMs until (accMs + duration)) {
                                currentPlayingClip = clip
                                currentClipStartMs = accMs
                                break
                            }
                            accMs += duration
                        }
                        if (currentPlayingClip == null) {
                            currentPlayingClip = project.videoClips.lastOrNull()
                            currentClipStartMs = project.videoClips.dropLast(1).sumOf { it.trimmedDurationMs }
                        }

                        val playheadInClipMs = (project.playheadPositionMs - currentClipStartMs).coerceAtLeast(0L)
                        val activeTransform = currentPlayingClip?.interpolatedTransformAt(playheadInClipMs)

                        // Live Transition Animate-In Calculation ONLY for current playing clip
                        val transDurationMs = (currentPlayingClip?.transitionDurationMs ?: 500L).coerceAtLeast(100L)
                        val transFrac = (playheadInClipMs.toFloat() / transDurationMs).coerceIn(0f, 1f)

                        val transOffsetX = if (playheadInClipMs < transDurationMs) {
                            when (currentPlayingClip?.transitionToNext) {
                                TransitionType.SLIDE_LEFT -> (1f - transFrac) * 400f   // SLIDE LEFT: Starts on RIGHT (+400dp), moves LEFT to 0 (Center)
                                TransitionType.SLIDE_RIGHT -> (1f - transFrac) * -400f // SLIDE RIGHT: Starts on LEFT (-400dp), moves RIGHT to 0 (Center)
                                else -> 0f
                            }
                        } else 0f

                        val transAlpha = if (playheadInClipMs < transDurationMs) {
                            when (currentPlayingClip?.transitionToNext) {
                                TransitionType.CROSSFADE, TransitionType.FADE_BLACK -> transFrac
                                else -> 1f
                            }
                        } else 1f

                        val clipScale = activeTransform?.scale ?: 1f
                        val clipRot = activeTransform?.rotationDegrees ?: 0f
                        val clipTransX = ((activeTransform?.translateXFraction ?: 0f) * 300f) + transOffsetX
                        val clipTransY = (activeTransform?.translateYFraction ?: 0f) * 300f
                        val clipAlpha = (activeTransform?.opacity ?: 1f) * transAlpha

                        val crop = currentPlayingClip?.cropRect ?: CropRect()
                        val cropW = (crop.right - crop.left).coerceIn(0.05f, 1f)
                        val cropH = (crop.bottom - crop.top).coerceIn(0.05f, 1f)
                        val cropCenterX = (crop.left + crop.right) / 2f
                        val cropCenterY = (crop.top + crop.bottom) / 2f

                        val cropZoomX = 1f / cropW
                        val cropZoomY = 1f / cropH
                        val totalScaleX = clipScale * cropZoomX
                        val totalScaleY = clipScale * cropZoomY

                        val cropShiftX = -(cropCenterX - 0.5f) * 300f * totalScaleX
                        val cropShiftY = -(cropCenterY - 0.5f) * 300f * totalScaleY

                        val finalTransX = clipTransX + cropShiftX
                        val finalTransY = clipTransY + cropShiftY

                        val flipX = if (currentPlayingClip?.isMirrored == true) -1f else 1f

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clipToBounds()
                                .graphicsLayer {
                                    scaleX = totalScaleX * flipX
                                    scaleY = totalScaleY
                                    rotationZ = clipRot
                                    translationX = finalTransX
                                    translationY = finalTransY
                                    alpha = clipAlpha
                                }
                        ) {
                            VideoPlayer(
                                videoClips = project.videoClips,
                                audioClips = project.audioClips,
                                muteOriginal = project.muteOriginalAudio,
                                isPlaying = isPlaying,
                                seekToMs = seekToMs,
                                onProgress = { pos ->
                                    onUpdateProject { p -> p.copy(playheadPositionMs = pos) }
                                }
                            )

                            // Live Color Filter Overlay
                            LiveColorFilterOverlay(
                                filterPreset = currentPlayingClip?.filterPreset,
                                modifier = Modifier.fillMaxSize()
                            )
                        }

                        TextOverlayLayer(
                            overlays = project.textLayers,
                            selectedId = project.selectedLayerId,
                            currentTimeMs = project.playheadPositionMs,
                            onSelectOverlay = { id ->
                                onUpdateProject { p -> p.copy(selectedLayerId = id) }
                            },
                            onOverlayMoved = { id, x, y ->
                                onUpdateProject { p ->
                                    p.copy(textLayers = p.textLayers.map {
                                        if (it.id == id) it.copy(xFraction = x, yFraction = y) else it
                                    })
                                }
                            },
                            onRemoveOverlay = { id ->
                                onUpdateProject { p ->
                                    val updated = p.textLayers.filterNot { it.id == id }
                                    p.copy(
                                        textLayers = updated,
                                        selectedLayerId = if (p.selectedLayerId == id) updated.lastOrNull()?.id else p.selectedLayerId
                                    )
                                }
                            }
                        )

                        ImageOverlayLayer(
                            overlays = project.imageOverlays,
                            selectedId = project.selectedLayerId,
                            currentTimeMs = project.playheadPositionMs,
                            onSelectOverlay = { id ->
                                onUpdateProject { p -> p.copy(selectedLayerId = id) }
                            },
                            onOverlayMoved = { id, x, y ->
                                onUpdateProject { p ->
                                    p.copy(imageOverlays = p.imageOverlays.map {
                                        if (it.id == id) it.copy(xFraction = x, yFraction = y) else it
                                    })
                                }
                            },
                            onRemoveOverlay = { id ->
                                onUpdateProject { p ->
                                    val updated = p.imageOverlays.filterNot { it.id == id }
                                    p.copy(
                                        imageOverlays = updated,
                                        selectedLayerId = if (p.selectedLayerId == id) updated.lastOrNull()?.id else p.selectedLayerId
                                    )
                                }
                            }
                        )
                    }
                }
            }

            // =========================
            // MULTI-TRACK TIMELINE
            // =========================
            if (project.videoClips.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                TimelineView(
                    videoClips = project.videoClips,
                    selectedVideoClipId = selectedVideoClip?.id,
                    audioClips = project.audioClips,
                    selectedAudioClipId = selectedAudioClip?.id,
                    textOverlays = project.textLayers,
                    selectedTextOverlayId = selectedTextLayer?.id,
                    imageOverlays = project.imageOverlays,
                    selectedImageOverlayId = selectedImageOverlay?.id,
                    currentTimeMs = project.playheadPositionMs,
                    isPlaying = isPlaying,
                    onPlayPauseToggle = { isPlaying = !isPlaying },
                    onSeekTo = { seekMs ->
                        seekToMs = seekMs
                        onUpdateProject { p -> p.copy(playheadPositionMs = seekMs) }
                    },
                    onSelectVideoClip = { id ->
                        onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        activeTool = EditorTool.TRIM
                    },
                    onDeleteVideoClip = { id ->
                        onUpdateProject { p ->
                            val updated = p.videoClips.filterNot { it.id == id }
                            p.copy(videoClips = updated, selectedLayerId = updated.firstOrNull()?.id)
                        }
                    },
                    onSelectAudioClip = { id ->
                        onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        activeTool = EditorTool.AUDIO
                    },
                    onSelectTextOverlay = { id ->
                        onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        activeTool = EditorTool.TEXT
                    },
                    onSelectImageOverlay = { id ->
                        onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        activeTool = EditorTool.OVERLAY
                    },
                    onSelectTransition = { id ->
                        onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        activeTool = EditorTool.TRANSITIONS
                    },
                    onAddVideo = onAddVideo,
                    onAddAudio = onAddAudio,
                    onAddText = {
                        val newText = TextOverlayItem(text = "Caption ${project.textLayers.size + 1}")
                        onUpdateProject { p ->
                            p.copy(
                                textLayers = p.textLayers + newText,
                                selectedLayerId = newText.id
                            )
                        }
                        activeTool = EditorTool.TEXT
                    },
                    onAddImageOverlay = onAddImageOverlay,
                    canvasAspectRatio = project.canvasSettings.aspectRatio,
                    onSplitSelectedAtPlayhead = {},
                    onMoveTextLayer = { id, newStart ->
                        onUpdateProject { p ->
                            p.copy(textLayers = p.textLayers.map {
                                if (it.id == id) it.copy(startTimeMs = newStart) else it
                            })
                        }
                    },
                    onResizeTextLayer = { id, newDuration ->
                        onUpdateProject { p ->
                            p.copy(textLayers = p.textLayers.map {
                                if (it.id == id) it.copy(durationMs = newDuration) else it
                            })
                        }
                    },
                    onDeleteTextLayer = { id ->
                        onUpdateProject { p ->
                            p.copy(textLayers = p.textLayers.filterNot { it.id == id })
                        }
                    },
                    onMoveImageOverlay = { id, newStart ->
                        onUpdateProject { p ->
                            p.copy(imageOverlays = p.imageOverlays.map {
                                if (it.id == id) it.copy(startTimeMs = newStart) else it
                            })
                        }
                    },
                    onResizeImageOverlay = { id, newDuration ->
                        onUpdateProject { p ->
                            p.copy(imageOverlays = p.imageOverlays.map {
                                if (it.id == id) it.copy(durationMs = newDuration) else it
                            })
                        }
                    },
                    onDeleteImageOverlay = { id ->
                        onUpdateProject { p ->
                            p.copy(imageOverlays = p.imageOverlays.filterNot { it.id == id })
                        }
                    },
                    onSplitAudioAt = { _, _ -> },
                    onMoveAudioClip = { id, newStart ->
                        onUpdateProject { p ->
                            p.copy(audioClips = p.audioClips.map {
                                if (it.id == id) it.copy(startTimeMs = newStart) else it
                            })
                        }
                    },
                    onDeleteAudioClip = { id ->
                        onUpdateProject { p ->
                            p.copy(audioClips = p.audioClips.filterNot { it.id == id })
                        }
                    },
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // =========================
            // ACTIVE TOOL PANEL
            // =========================
            AnimatedVisibility(
                visible = project.videoClips.isNotEmpty() && activeTool != EditorTool.NONE,
                enter = fadeIn(animationSpec = tween(220)) + expandVertically(animationSpec = tween(220)),
                exit = fadeOut(animationSpec = tween(180)) + shrinkVertically(animationSpec = tween(180))
            ) {
                when (activeTool) {
                    EditorTool.CLIPS -> ClipsPanel(
                        videoClips = project.videoClips,
                        selectedClipId = selectedVideoClip?.id,
                        onSelectClip = { id ->
                            onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        },
                        onMoveClipUp = { idx ->
                            onUpdateProject { p ->
                                val mutable = p.videoClips.toMutableList()
                                val item = mutable.removeAt(idx)
                                mutable.add(idx - 1, item)
                                p.copy(videoClips = mutable)
                            }
                        },
                        onMoveClipDown = { idx ->
                            onUpdateProject { p ->
                                val mutable = p.videoClips.toMutableList()
                                val item = mutable.removeAt(idx)
                                mutable.add(idx + 1, item)
                                p.copy(videoClips = mutable)
                            }
                        },
                        onRemoveClip = { id ->
                            onUpdateProject { p ->
                                val updated = p.videoClips.filterNot { it.id == id }
                                p.copy(
                                    videoClips = updated,
                                    selectedLayerId = if (p.selectedLayerId == id) updated.firstOrNull()?.id else p.selectedLayerId
                                )
                            }
                        },
                        onAddVideo = onAddVideo,
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.TRIM -> TrimPanel(
                        selectedClip = selectedVideoClip,
                        onTrimChanged = { startMs, endMs ->
                            if (selectedVideoClip != null) {
                                onUpdateProject { p ->
                                    p.copy(videoClips = p.videoClips.map {
                                        if (it.id == selectedVideoClip.id) it.copy(trimStartMs = startMs, trimEndMs = endMs)
                                        else it
                                    })
                                }
                            }
                        },
                        onDeleteClip = if (selectedVideoClip != null) {
                            {
                                onUpdateProject { p ->
                                    val updated = p.videoClips.filterNot { it.id == selectedVideoClip.id }
                                    p.copy(videoClips = updated, selectedLayerId = updated.firstOrNull()?.id)
                                }
                            }
                        } else null,
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.SPLIT -> SplitPanel(
                        selectedClip = selectedVideoClip,
                        selectedAudio = selectedAudioClip,
                        selectedText = selectedTextLayer,
                        selectedOverlay = selectedImageOverlay,
                        currentPositionMs = project.playheadPositionMs,
                        clipStartInProjectMs = selectedClipStartInProjectMs,
                        onSplitVideo = {
                            if (selectedVideoClip != null) {
                                val clipIndex = project.videoClips.indexOfFirst { it.id == selectedVideoClip.id }
                                if (clipIndex >= 0) {
                                    val clipOffsetMs = project.playheadPositionMs - selectedClipStartInProjectMs
                                    val splitPointSourceMs = selectedVideoClip.trimStartMs + (clipOffsetMs * selectedVideoClip.speed).toLong()

                                    if (splitPointSourceMs > selectedVideoClip.trimStartMs + 200L &&
                                        splitPointSourceMs < selectedVideoClip.trimEndMs - 200L
                                    ) {
                                        val clipA = selectedVideoClip.copy(id = UUID.randomUUID().toString(), trimEndMs = splitPointSourceMs)
                                        val clipB = selectedVideoClip.copy(id = UUID.randomUUID().toString(), trimStartMs = splitPointSourceMs)

                                        onUpdateProject { p ->
                                            val mutable = p.videoClips.toMutableList()
                                            mutable.removeAt(clipIndex)
                                            mutable.add(clipIndex, clipB)
                                            mutable.add(clipIndex, clipA)
                                            p.copy(videoClips = mutable, selectedLayerId = clipA.id)
                                        }
                                    }
                                }
                            }
                        },
                        onSplitAudio = {
                            if (selectedAudioClip != null) {
                                val audioIndex = project.audioClips.indexOfFirst { it.id == selectedAudioClip.id }
                                if (audioIndex >= 0) {
                                    val audioOffsetMs = project.playheadPositionMs - selectedAudioClip.startTimeMs
                                    val splitPointSourceMs = selectedAudioClip.trimStartMs + audioOffsetMs

                                    if (splitPointSourceMs > selectedAudioClip.trimStartMs + 200L &&
                                        splitPointSourceMs < selectedAudioClip.trimEndMs - 200L
                                    ) {
                                        val audioA = selectedAudioClip.copy(id = UUID.randomUUID().toString(), trimEndMs = splitPointSourceMs)
                                        val audioB = selectedAudioClip.copy(
                                            id = UUID.randomUUID().toString(),
                                            trimStartMs = splitPointSourceMs,
                                            startTimeMs = project.playheadPositionMs
                                        )

                                        onUpdateProject { p ->
                                            val mutable = p.audioClips.toMutableList()
                                            mutable.removeAt(audioIndex)
                                            mutable.add(audioIndex, audioB)
                                            mutable.add(audioIndex, audioA)
                                            p.copy(audioClips = mutable, selectedLayerId = audioA.id)
                                        }
                                    }
                                }
                            }
                        },
                        onSplitText = {
                            val targetText = selectedTextLayer ?: project.textLayers.find {
                                project.playheadPositionMs in it.startTimeMs until (it.startTimeMs + it.durationMs)
                            }
                            if (targetText != null) {
                                val textIndex = project.textLayers.indexOfFirst { it.id == targetText.id }
                                if (textIndex >= 0) {
                                    val offsetMs = project.playheadPositionMs - targetText.startTimeMs
                                    if (offsetMs > 100L && offsetMs < targetText.durationMs - 100L) {
                                        val textA = targetText.copy(id = UUID.randomUUID().toString(), durationMs = offsetMs)
                                        val textB = targetText.copy(
                                            id = UUID.randomUUID().toString(),
                                            startTimeMs = project.playheadPositionMs,
                                            durationMs = targetText.durationMs - offsetMs
                                        )
                                        onUpdateProject { p ->
                                            val mutable = p.textLayers.toMutableList()
                                            mutable.removeAt(textIndex)
                                            mutable.add(textIndex, textB)
                                            mutable.add(textIndex, textA)
                                            p.copy(textLayers = mutable, selectedLayerId = textA.id)
                                        }
                                    }
                                }
                            }
                        },
                        onSplitOverlay = {
                            val targetOverlay = selectedImageOverlay ?: project.imageOverlays.find {
                                project.playheadPositionMs in it.startTimeMs until (it.startTimeMs + it.durationMs)
                            }
                            if (targetOverlay != null) {
                                val overlayIndex = project.imageOverlays.indexOfFirst { it.id == targetOverlay.id }
                                if (overlayIndex >= 0) {
                                    val offsetMs = project.playheadPositionMs - targetOverlay.startTimeMs
                                    if (offsetMs > 100L && offsetMs < targetOverlay.durationMs - 100L) {
                                        val overlayA = targetOverlay.copy(id = UUID.randomUUID().toString(), durationMs = offsetMs)
                                        val overlayB = targetOverlay.copy(
                                            id = UUID.randomUUID().toString(),
                                            startTimeMs = project.playheadPositionMs,
                                            durationMs = targetOverlay.durationMs - offsetMs
                                        )
                                        onUpdateProject { p ->
                                            val mutable = p.imageOverlays.toMutableList()
                                            mutable.removeAt(overlayIndex)
                                            mutable.add(overlayIndex, overlayB)
                                            mutable.add(overlayIndex, overlayA)
                                            p.copy(imageOverlays = mutable, selectedLayerId = overlayA.id)
                                        }
                                    }
                                }
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.TRANSFORM -> TransformPanel(
                        selectedClip = selectedVideoClip,
                        canvasAspectRatio = project.canvasSettings.aspectRatio,
                        onTransformChanged = { scale, transX, transY, rot, opacity, isMirrored ->
                            if (selectedVideoClip != null) {
                                onUpdateProject { p ->
                                    p.copy(videoClips = p.videoClips.map {
                                        if (it.id == selectedVideoClip.id) it.copy(
                                            scale = scale,
                                            translateXFraction = transX,
                                            translateYFraction = transY,
                                            rotationDegrees = rot,
                                            opacity = opacity,
                                            isMirrored = isMirrored
                                        ) else it
                                    })
                                }
                            }
                        },
                        onDeleteClip = if (selectedVideoClip != null) {
                            {
                                onUpdateProject { p ->
                                    val updated = p.videoClips.filterNot { it.id == selectedVideoClip.id }
                                    p.copy(videoClips = updated, selectedLayerId = updated.firstOrNull()?.id)
                                }
                                activeTool = EditorTool.NONE
                            }
                        } else null,
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.TEXT -> TextPanel(
                        overlays = project.textLayers,
                        selectedId = selectedTextLayer?.id,
                        onSelectOverlay = { id ->
                            onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        },
                        onAdd = { text ->
                            val item = TextOverlayItem(text = text)
                            onUpdateProject { p ->
                                p.copy(
                                    textLayers = p.textLayers + item,
                                    selectedLayerId = item.id
                                )
                            }
                        },
                        onRemove = { id ->
                            onUpdateProject { p ->
                                val updated = p.textLayers.filterNot { it.id == id }
                                p.copy(
                                    textLayers = updated,
                                    selectedLayerId = if (p.selectedLayerId == id) updated.lastOrNull()?.id else p.selectedLayerId
                                )
                            }
                        },
                        onUpdateOverlay = { updated ->
                            onUpdateProject { p ->
                                p.copy(textLayers = p.textLayers.map { if (it.id == updated.id) updated else it })
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.AUDIO -> AudioPanel(
                        audioClips = project.audioClips,
                        selectedAudioId = selectedAudioClip?.id,
                        muteOriginal = project.muteOriginalAudio,
                        currentTimeMs = project.playheadPositionMs,
                        onPickAudio = onAddAudio,
                        onRemoveAudio = { id ->
                            onUpdateProject { p ->
                                val updated = p.audioClips.filterNot { it.id == id }
                                p.copy(
                                    audioClips = updated,
                                    selectedLayerId = if (p.selectedLayerId == id) updated.firstOrNull()?.id else p.selectedLayerId
                                )
                            }
                        },
                        onUpdateAudio = { updated ->
                            onUpdateProject { p ->
                                p.copy(audioClips = p.audioClips.map { if (it.id == updated.id) updated else it })
                            }
                        },
                        onMuteToggle = { mute ->
                            onUpdateProject { p -> p.copy(muteOriginalAudio = mute) }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.SPEED -> SpeedPanel(
                        selectedClip = selectedVideoClip,
                        onSpeedChanged = { newSpeed ->
                            if (selectedVideoClip != null) {
                                onUpdateProject { p ->
                                    p.copy(videoClips = p.videoClips.map {
                                        if (it.id == selectedVideoClip.id) it.copy(speed = newSpeed) else it
                                    })
                                }
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.TRANSITIONS -> TransitionsPanel(
                        selectedClip = selectedVideoClip,
                        onTransitionChanged = { transitionType, durationMs ->
                            if (selectedVideoClip != null) {
                                onUpdateProject { p ->
                                    p.copy(videoClips = p.videoClips.map {
                                        if (it.id == selectedVideoClip.id) it.copy(transitionToNext = transitionType, transitionDurationMs = durationMs) else it
                                    })
                                }
                                seekToMs = selectedClipStartInProjectMs
                                isPlaying = true
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.FILTERS -> FiltersPanel(
                        selectedClip = selectedVideoClip,
                        onFilterChanged = { newFilter ->
                            if (selectedVideoClip != null) {
                                onUpdateProject { p ->
                                    p.copy(videoClips = p.videoClips.map {
                                        if (it.id == selectedVideoClip.id) it.copy(filterPreset = newFilter) else it
                                    })
                                }
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.KEYFRAMES -> {
                        var clipUnderPlayhead: VideoClip? = null
                        var clipStartMs = 0L
                        var accMs = 0L

                        for (clip in project.videoClips) {
                            val len = clip.trimmedDurationMs
                            if (project.playheadPositionMs in accMs until (accMs + len) || clip == project.videoClips.last()) {
                                clipUnderPlayhead = clip
                                clipStartMs = accMs
                                break
                            }
                            accMs += len
                        }

                        val targetClip = clipUnderPlayhead ?: selectedVideoClip

                        KeyframesPanel(
                            selectedClip = targetClip,
                            currentTimeMs = project.playheadPositionMs,
                            clipStartInProjectMs = clipStartMs,
                            onAddKeyframe = { kf ->
                                if (targetClip != null) {
                                    onUpdateProject { p ->
                                        p.copy(videoClips = p.videoClips.map { clip ->
                                            if (clip.id == targetClip.id) {
                                                val filtered = clip.keyframes.filterNot { existing -> abs(existing.timeMs - kf.timeMs) < 100L }
                                                clip.copy(keyframes = (filtered + kf).sortedBy { item -> item.timeMs })
                                            } else clip
                                        })
                                    }
                                }
                            },
                            onRemoveKeyframe = { kfId ->
                                if (targetClip != null) {
                                    onUpdateProject { p ->
                                        p.copy(videoClips = p.videoClips.map { clip ->
                                            if (clip.id == targetClip.id) clip.copy(keyframes = clip.keyframes.filterNot { kf -> kf.id == kfId }) else clip
                                        })
                                    }
                                }
                            },
                            onClose = { activeTool = EditorTool.NONE },
                            modifier = Modifier.padding(horizontal = 20.dp)
                        )
                    }
                    EditorTool.OVERLAY -> OverlayPanel(
                        overlays = project.imageOverlays,
                        selectedId = selectedImageOverlay?.id,
                        onSelectOverlay = { id ->
                            onUpdateProject { p -> p.copy(selectedLayerId = id) }
                        },
                        onPickImage = onAddImageOverlay,
                        onRemove = { id ->
                            onUpdateProject { p ->
                                val updated = p.imageOverlays.filterNot { it.id == id }
                                p.copy(
                                    imageOverlays = updated,
                                    selectedLayerId = if (p.selectedLayerId == id) updated.lastOrNull()?.id else p.selectedLayerId
                                )
                            }
                        },
                        onUpdateOverlay = { updated ->
                            onUpdateProject { p ->
                                p.copy(imageOverlays = p.imageOverlays.map { if (it.id == updated.id) updated else it })
                            }
                        },
                        onClose = { activeTool = EditorTool.NONE },
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                    EditorTool.NONE -> {}
                }
            }
        }
    }
}

fun shareExportedVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Share Video"))
}

fun openExportedVideo(context: Context, uri: Uri) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "video/mp4")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(intent)
}

private fun toggleTool(current: EditorTool, tapped: EditorTool): EditorTool =
    if (current == tapped) EditorTool.NONE else tapped

@Composable
private fun IconTextButton(
    icon: ImageVector,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .background(
                if (enabled) CanvasEditorColors.AccentPurple else CanvasEditorColors.CardDark,
                RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        if (enabled) {
            Icon(icon, contentDescription = label, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, color = Color.White, fontWeight = FontWeight.Bold)
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                color = Color.White,
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Exporting…", color = Color.White)
        }
    }
}

// ========================================
// VIDEO PLAYER
// ========================================

@UnstableApi
@Composable
fun VideoPlayer(
    videoClips: List<VideoClip>,
    audioClips: List<AudioClip>,
    muteOriginal: Boolean = false,
    isPlaying: Boolean = true,
    seekToMs: Long? = null,
    onProgress: (Long) -> Unit = {}
) {
    val context = LocalContext.current

    val videoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
    }

    val audioPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = ExoPlayer.REPEAT_MODE_ALL
        }
    }

    // Update video playlist whenever videoClips change
    LaunchedEffect(videoClips) {
        if (videoClips.isNotEmpty()) {
            val mediaItems = videoClips.map { clip ->
                MediaItem.Builder()
                    .setUri(clip.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(clip.trimStartMs)
                            .setEndPositionMs(clip.trimEndMs)
                            .build()
                    )
                    .build()
            }
            videoPlayer.setMediaItems(mediaItems)
            videoPlayer.prepare()
            videoPlayer.playWhenReady = isPlaying
        } else {
            videoPlayer.clearMediaItems()
        }
    }

    // Handle speed on playback
    LaunchedEffect(videoClips) {
        if (videoClips.isNotEmpty()) {
            val currentIdx = videoPlayer.currentMediaItemIndex
            val speed = videoClips.getOrNull(currentIdx)?.speed ?: 1.0f
            videoPlayer.playbackParameters = PlaybackParameters(speed)
        }
    }

    // Update audio playlist whenever audioClips change
    LaunchedEffect(audioClips) {
        val activeAudios = audioClips.filter { !it.isMuted }
        if (activeAudios.isNotEmpty()) {
            val items = activeAudios.map { audio ->
                MediaItem.Builder()
                    .setUri(audio.uri)
                    .setClippingConfiguration(
                        MediaItem.ClippingConfiguration.Builder()
                            .setStartPositionMs(audio.trimStartMs)
                            .setEndPositionMs(audio.trimEndMs)
                            .build()
                    )
                    .build()
            }
            audioPlayer.setMediaItems(items)
            audioPlayer.prepare()
            audioPlayer.playWhenReady = videoPlayer.isPlaying
        } else {
            audioPlayer.clearMediaItems()
        }
    }

    // Handle Volume / Mute
    LaunchedEffect(muteOriginal) {
        videoPlayer.volume = if (muteOriginal) 0f else 1f
    }

    // Handle Play / Pause
    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            videoPlayer.play()
            if (audioClips.any { !it.isMuted }) audioPlayer.play()
        } else {
            videoPlayer.pause()
            audioPlayer.pause()
        }
    }

    // Handle Seeking across multi-video project timeline
    LaunchedEffect(seekToMs) {
        seekToMs?.let { targetMs ->
            if (videoClips.isNotEmpty()) {
                var accMs = 0L
                var targetIdx = 0
                var clipOffsetProjectMs = targetMs

                for (i in videoClips.indices) {
                    val len = videoClips[i].trimmedDurationMs
                    if (targetMs <= accMs + len || i == videoClips.size - 1) {
                        targetIdx = i
                        clipOffsetProjectMs = (targetMs - accMs).coerceIn(0L, len)
                        break
                    }
                    accMs += len
                }

                val targetClip = videoClips.getOrNull(targetIdx)
                val speed = targetClip?.speed ?: 1.0f
                val sourceClipOffsetMs = (clipOffsetProjectMs * speed).toLong()

                videoPlayer.seekTo(targetIdx, sourceClipOffsetMs)
                if (audioClips.isNotEmpty()) {
                    audioPlayer.seekTo(targetMs)
                }
            }
        }
    }

    // Monitor progress
    LaunchedEffect(videoPlayer, videoClips) {
        while (true) {
            if (videoPlayer.isPlaying && videoClips.isNotEmpty()) {
                val currentIdx = videoPlayer.currentMediaItemIndex
                val rawPosInClipMs = videoPlayer.currentPosition

                var projectPosMs = 0L
                for (i in 0 until currentIdx.coerceAtMost(videoClips.size)) {
                    projectPosMs += videoClips[i].trimmedDurationMs
                }
                val currentClip = videoClips.getOrNull(currentIdx)
                val clipSpeed = currentClip?.speed ?: 1.0f
                val realPosInClipMs = (rawPosInClipMs / clipSpeed).toLong()
                projectPosMs += realPosInClipMs

                onProgress(projectPosMs)
            }
            delay(50)
        }
    }

    // Clean lifecycle on exit
    DisposableEffect(Unit) {
        onDispose {
            videoPlayer.release()
            audioPlayer.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
            }
        },
        update = { playerView ->
            if (playerView.player != videoPlayer) {
                playerView.player = videoPlayer
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun LiveColorFilterOverlay(
    filterPreset: ColorFilterPreset?,
    modifier: Modifier = Modifier
) {
    if (filterPreset == null || filterPreset == ColorFilterPreset.NONE) return

    val (color, blendMode) = when (filterPreset) {
        ColorFilterPreset.VIVID -> Color(0xFF8B5CF6).copy(alpha = 0.20f) to BlendMode.Overlay
        ColorFilterPreset.VINTAGE -> Color(0xFFD97706).copy(alpha = 0.25f) to BlendMode.Softlight
        ColorFilterPreset.B_AND_W -> Color(0xFF18181B).copy(alpha = 0.88f) to BlendMode.Color
        ColorFilterPreset.CINEMATIC_TEAL_ORANGE -> Color(0xFF06B6D4).copy(alpha = 0.20f) to BlendMode.Overlay
        ColorFilterPreset.WARM -> Color(0xFFF59E0B).copy(alpha = 0.22f) to BlendMode.Softlight
        ColorFilterPreset.COOL -> Color(0xFF3B82F6).copy(alpha = 0.20f) to BlendMode.Overlay
        ColorFilterPreset.SEPIA -> Color(0xFF78350F).copy(alpha = 0.50f) to BlendMode.Color
        ColorFilterPreset.NONE -> return
    }

    Canvas(modifier = modifier) {
        drawRect(color = color, blendMode = blendMode)
    }
}

// ========================================
// BOTTOM TOOL BUTTON (Min 48dp Touch Target)
// ========================================

@Composable
fun EditorToolButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val animatedBg by animateColorAsState(
        targetValue = if (selected) CanvasEditorColors.CardDarkElevated else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "toolBg"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(animatedBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (selected) CanvasEditorColors.AccentPurple else CanvasEditorColors.TextPrimary,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            color = if (selected) CanvasEditorColors.AccentPurple else CanvasEditorColors.TextSecondary,
            fontSize = 8.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}