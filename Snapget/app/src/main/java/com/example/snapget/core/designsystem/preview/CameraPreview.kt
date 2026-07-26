package com.example.snapget.core.designsystem.preview

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.util.Log
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraControl
import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.snapget.core.constants.MAX_VIDEO_SECONDS
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CameraPreviewWithZoom(
    lifecycleOwner: LifecycleOwner,
    modifier: Modifier = Modifier,
    height: Dp = 300.dp,
    onPhotoTaken: ((String) -> Unit)? = null,
    // Giu nut chup de quay video (<=5s). null = tat quay video
    onVideoTaken: ((String) -> Unit)? = null,
    showControls: Boolean = true,
    // Tang gia tri nay tu ngoai (nut center bottom bar) de chup 1 tam anh.
    // 0 = chua yeu cau. (Nut chup TRONG preview da xoa 2026-07-26 — trung nut.)
    captureRequestId: Int = 0,
    // Tang tu ngoai de BAT DAU quay video (giu nut center) / DUNG quay (tha tay).
    startRecordRequestId: Int = 0,
    stopRecordRequestId: Int = 0,
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    // Calculate the actual modifier to use
    val sizeModifier = modifier
        .height(height)
        .fillMaxWidth()
        .clip(RoundedCornerShape(16.dp))

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.CAMERA,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (!granted) {
            Log.e("CameraPreview", "Camera permission denied")
        }
    }

    // UI changes based on permission state
    if (hasPermission) {
        var flashEnabled by remember { mutableStateOf(false) }
        var zoomRatio by remember { mutableFloatStateOf(1.0f) }
        var minZoom by remember { mutableFloatStateOf(1.0f) }
        var maxZoom by remember { mutableFloatStateOf(1.0f) }
        var cameraControl: CameraControl? by remember { mutableStateOf(null) }
        var cameraInfo: CameraInfo? by remember { mutableStateOf(null) }
        var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
        var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
        var activeRecording: Recording? by remember { mutableStateOf(null) }
        var isRecording by remember { mutableStateOf(false) }
        var recordElapsedMs by remember { mutableLongStateOf(0L) }
        var isWideMode by remember { mutableStateOf(false) }
        var isFrontCamera by remember { mutableStateOf(false) }

        // Dem thoi gian quay + TU DONG DUNG khi cham 5s (server cung enforce)
        LaunchedEffect(isRecording) {
            if (isRecording) {
                recordElapsedMs = 0L
                while (isRecording && recordElapsedMs < MAX_VIDEO_SECONDS * 1000L) {
                    delay(100)
                    recordElapsedMs += 100
                }
                if (isRecording) {
                    activeRecording?.stop() // Finalize callback se tra file ve onVideoTaken
                }
            }
        }

        // Enhanced UX states
        var isLoading by remember { mutableStateOf(true) }
        var showFocusIndicator by remember { mutableStateOf(false) }
        var focusPoint by remember { mutableStateOf(Pair(0f, 0f)) }
        var showZoomControls by remember { mutableStateOf(false) }
        var lastInteractionTime by remember { mutableLongStateOf(0L) }
        var isFlashAnimating by remember { mutableStateOf(false) }
        var showZoomValue by remember { mutableStateOf(false) }

        var currentPreviewView: PreviewView? by remember { mutableStateOf(null) }

        // Auto-hide zoom controls after 3 seconds of inactivity
        LaunchedEffect(lastInteractionTime) {
            if (lastInteractionTime > 0) {
                delay(3000)
                if (System.currentTimeMillis() - lastInteractionTime >= 3000) {
                    showZoomControls = false
                }
            }
        }

        // Auto-hide zoom value after 2 seconds
        LaunchedEffect(showZoomValue) {
            if (showZoomValue) {
                delay(2000)
                showZoomValue = false
            }
        }

        Box(modifier = sizeModifier) {
            var previewView: PreviewView? by remember { mutableStateOf(null) }

            AndroidView(
                modifier = Modifier
                    .matchParentSize()
                    // Pinch-zoom 2 NGON: chi tieu thu event khi >=2 ngon dang cham —
                    // vuot 1 ngon van loi ra ngoai cho gesture "vuot len mo feed"
                    // cua CameraScreen (dung detectTransformGestures se nuot ca pan 1 ngon)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            do {
                                val event = awaitPointerEvent()
                                if (event.changes.count { it.pressed } >= 2) {
                                    val zoomChange = event.calculateZoom()
                                    if (zoomChange != 1f) {
                                        val newZoom = (zoomRatio * zoomChange)
                                            .coerceIn(minZoom, maxZoom)
                                        cameraControl?.setZoomRatio(newZoom)
                                        showZoomControls = true
                                        showZoomValue = true
                                        lastInteractionTime = System.currentTimeMillis()
                                    }
                                    event.changes.forEach { it.consume() }
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = { offset ->
                                // Enhanced tap-to-focus with visual feedback
                                focusPoint = Pair(offset.x, offset.y)
                                showFocusIndicator = true
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                // Handle tap-to-focus
                                cameraControl?.let { control ->
                                    currentPreviewView?.meteringPointFactory?.let { factory ->
                                        val point = factory.createPoint(offset.x, offset.y)
                                        val action = FocusMeteringAction.Builder(point).build()
                                        control.startFocusAndMetering(action)
                                    }
                                }
                            },
                        )
                    },
                factory = { ctx ->
                    val localPreviewView = PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }

                    previewView = localPreviewView
                    currentPreviewView = localPreviewView

                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        try {
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .build()

                            preview.surfaceProvider = localPreviewView.surfaceProvider

                            val imageCaptureUseCase = ImageCapture.Builder()
                                .setTargetResolution(Size(1280, 720))
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                .build()

                            imageCapture = imageCaptureUseCase

                            val cameraSelector = if (isFrontCamera) {
                                CameraSelector.DEFAULT_FRONT_CAMERA
                            } else {
                                CameraSelector.DEFAULT_BACK_CAMERA
                            }

                            cameraProvider.unbindAll()
                            // Thu bind kem VideoCapture (quay video ngan). May LIMITED khong du
                            // stream -> fallback bind khong co video, chi chup anh.
                            val camera = if (onVideoTaken != null) {
                                val recorder = Recorder.Builder()
                                    .setQualitySelector(QualitySelector.from(Quality.HD))
                                    .build()
                                val videoCaptureUseCase = VideoCapture.withOutput(recorder)
                                try {
                                    val cam = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCaptureUseCase,
                                        videoCaptureUseCase,
                                    )
                                    videoCapture = videoCaptureUseCase
                                    cam
                                } catch (e: Exception) {
                                    Log.w("CameraPreview", "Khong bind duoc VideoCapture, fallback chi chup anh", e)
                                    videoCapture = null
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        cameraSelector,
                                        preview,
                                        imageCaptureUseCase,
                                    )
                                }
                            } else {
                                cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    imageCaptureUseCase,
                                )
                            }
                            cameraControl = camera.cameraControl
                            cameraInfo = camera.cameraInfo

                            // Observe zoom state with enhanced feedback
                            cameraInfo?.zoomState?.observe(lifecycleOwner) { zoomState ->
                                zoomRatio = zoomState.zoomRatio
                                minZoom = zoomState.minZoomRatio
                                maxZoom = zoomState.maxZoomRatio
                            }

                            // (Pinch-zoom cu bang setOnTouchListener DA XOA 2026-07-26 —
                            // tra ve false nen mat ca gesture, khong zoom duoc. Thay bang
                            // pointerInput 2 ngon tren AndroidView ben duoi.)

                            isLoading = false
                        } catch (exc: Exception) {
                            Log.e("CameraPreview", "Use case binding failed", exc)
                            isLoading = false
                        }
                    }, ContextCompat.getMainExecutor(ctx))

                    localPreviewView
                },
            )

            // Loading indicator
//            AnimatedVisibility(
//                visible = isLoading,
//                enter = fadeIn(),
//                exit = fadeOut()
//            ) {
//                Box(
//                    modifier = Modifier
//                        .fillMaxSize()
//                        .background(Color.Black.copy(alpha = 0.8f)),
//                    contentAlignment = Alignment.Center
//                ) {
//                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
//                        CircularProgressIndicator(color = Color.White)
//                        Spacer(modifier = Modifier.height(16.dp))
//                        Text(
//                            text = "Initializing Camera...",
//                            color = Color.White,
//                            fontSize = 16.sp
//                        )
//                    }
//                }
//            }

            // Nut chup — CHAM = chup anh, GIU = quay video <=5s (tha tay / het gio thi dung).
            // File luu vao cacheDir roi tra duong dan qua callback tuong ung.
            if (onPhotoTaken != null) {
                // Xin quyen ghi am 1 lan de video co tieng (tu choi van quay duoc, khong tieng)
                val audioLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission(),
                ) { }
                LaunchedEffect(onVideoTaken != null) {
                    if (onVideoTaken != null &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO,
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        audioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                fun takePhoto() {
                    val capture = imageCapture ?: return
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    val photoFile = File(
                        context.cacheDir,
                        "snapget_${System.currentTimeMillis()}.jpg",
                    )
                    val output = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                    capture.takePicture(
                        output,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults) {
                                onPhotoTaken(photoFile.absolutePath)
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraPreview", "Chup anh that bai: ${exception.message}")
                            }
                        },
                    )
                }

                // Yeu cau chup tu ngoai (nut center bottom bar CameraScreen)
                LaunchedEffect(captureRequestId) {
                    if (captureRequestId > 0) {
                        takePhoto()
                    }
                }

                @SuppressLint("MissingPermission")
                fun startRecording() {
                    if (onVideoTaken == null || isRecording) return
                    val capture = videoCapture ?: return
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    val videoFile = File(
                        context.cacheDir,
                        "snapget_${System.currentTimeMillis()}.mp4",
                    )
                    val pending = capture.output.prepareRecording(
                        context,
                        FileOutputOptions.Builder(videoFile).build(),
                    )
                    val hasAudio = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO,
                    ) == PackageManager.PERMISSION_GRANTED
                    val ready = if (hasAudio) pending.withAudioEnabled() else pending
                    activeRecording = ready.start(ContextCompat.getMainExecutor(context)) { event ->
                        if (event is VideoRecordEvent.Finalize) {
                            isRecording = false
                            activeRecording = null
                            if (!event.hasError()) {
                                onVideoTaken(videoFile.absolutePath)
                            } else {
                                Log.e("CameraPreview", "Quay video loi: ${event.error}")
                            }
                        }
                    }
                    isRecording = true
                }

                // Yeu cau quay video tu ngoai: GIU nut center bottom bar = bat dau, THA = dung
                LaunchedEffect(startRecordRequestId) {
                    if (startRecordRequestId > 0) {
                        startRecording()
                    }
                }
                LaunchedEffect(stopRecordRequestId) {
                    if (stopRecordRequestId > 0 && isRecording) {
                        activeRecording?.stop() // Finalize callback tra file ve onVideoTaken
                    }
                }

                // Dong ho dem giay khi dang quay
                if (isRecording) {
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.6f),
                    ) {
                        Text(
                            text = "● ${recordElapsedMs / 1000}s / ${MAX_VIDEO_SECONDS}s",
                            color = Color.Red,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                }

                // (Nut chup 72dp trong preview DA XOA 2026-07-26 — trung voi nut center
                // bottom bar. Chup/quay deu dieu khien tu ngoai qua captureRequestId +
                // start/stopRecordRequestId; khi quay chi con vong tien do lam feedback.)
                if (isRecording) {
                    androidx.compose.material3.CircularProgressIndicator(
                        progress = {
                            (recordElapsedMs.toFloat() / (MAX_VIDEO_SECONDS * 1000f))
                                .coerceIn(0f, 1f)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 20.dp)
                            .size(56.dp),
                        color = Color.Red,
                        strokeWidth = 4.dp,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }

            // Enhanced flash toggle with animation (only show if controls enabled)
            if (showControls) {
                val flashScale by animateFloatAsState(
                    targetValue = if (isFlashAnimating) 1.2f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                )

                Box(
                    modifier = Modifier
                        .padding(16.dp)
                        .align(Alignment.TopStart),
                ) {
                    Surface(
                        modifier = Modifier
                            .size(48.dp)
                            .scale(flashScale),
                        shape = CircleShape,
                        color = if (flashEnabled) {
                            Color.Yellow.copy(alpha = 0.9f)
                        } else {
                            Color.Black.copy(
                                alpha = 0.6f,
                            )
                        },
                        shadowElevation = if (flashEnabled) 8.dp else 2.dp,
                    ) {
                        IconButton(
                            onClick = {
                                flashEnabled = !flashEnabled
                                cameraControl?.enableTorch(flashEnabled)
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                                // Flash animation
                                isFlashAnimating = true
                                // Reset animation after a short delay
                                kotlinx.coroutines.GlobalScope.launch {
                                    delay(200)
                                    isFlashAnimating = false
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                                contentDescription = "Flash",
                                tint = if (flashEnabled) Color.Black else Color.White,
                                modifier = Modifier.size(24.dp),
                            )
                        }
                    }
                }

                // Enhanced zoom display with better styling (only show if controls enabled)
                if (showControls) {
                    AnimatedVisibility(
                        visible = showZoomValue,
                        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.TopEnd),
                    ) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color.Black.copy(alpha = 0.8f),
                            shadowElevation = 4.dp,
                        ) {
                            Text(
                                text = "${String.format("%.1f", zoomRatio)}x",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }

                    // Wide mode toggle (only show if controls enabled)
                    if (showControls) {
                        Box(
                            modifier = Modifier
                                .padding(16.dp)
                                .align(Alignment.TopEnd)
                                .offset(y = 60.dp),
                        ) {
                            Surface(
                                modifier = Modifier.clickable {
                                    isWideMode = !isWideMode
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    cameraControl?.let { control ->
                                        val targetZoom = if (isWideMode) 0.5f else 1.0f
                                        control.setLinearZoom(if (isWideMode) 0f else 0.5f)
                                    }
                                    showZoomValue = true
                                },
                                shape = CircleShape,
                                color = if (isWideMode) {
                                    Color.Blue.copy(alpha = 0.8f)
                                } else {
                                    Color.Black.copy(
                                        alpha = 0.6f,
                                    )
                                },
                                shadowElevation = 2.dp,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .padding(8.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = if (isWideMode) "W" else "N",
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                }
                            }
                        }

                        // Enhanced zoom controls (only show if controls enabled)
                        if (showControls) {
                            AnimatedVisibility(
                                visible = showZoomControls,
                                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 32.dp),
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(25.dp),
                                    color = Color.Black.copy(alpha = 0.8f),
                                    shadowElevation = 8.dp,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(
                                            horizontal = 20.dp,
                                            vertical = 12.dp,
                                        ),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    ) {
                                        ZoomButton(
                                            icon = Icons.Default.Remove,
                                            onClick = {
                                                val newZoom =
                                                    (zoomRatio - 0.2f).coerceAtLeast(minZoom)
                                                cameraControl?.setZoomRatio(newZoom)
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                                showZoomValue = true
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                        )

                                        Text(
                                            text = "${String.format("%.1f", zoomRatio)}x",
                                            color = Color.White,
                                            fontSize = 18.sp,
                                            fontWeight = FontWeight.Medium,
                                        )

                                        ZoomButton(
                                            icon = Icons.Default.Add,
                                            onClick = {
                                                val newZoom =
                                                    (zoomRatio + 0.2f).coerceAtMost(maxZoom)
                                                cameraControl?.setZoomRatio(newZoom)
                                                hapticFeedback.performHapticFeedback(
                                                    HapticFeedbackType.TextHandleMove,
                                                )
                                                showZoomValue = true
                                                lastInteractionTime = System.currentTimeMillis()
                                            },
                                        )
                                    }
                                }
                            }

                            // Focus indicator with animation
                            AnimatedVisibility(
                                visible = showFocusIndicator,
                                enter = scaleIn() + fadeIn(),
                                exit = scaleOut() + fadeOut(),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .offset(
                                            x = (focusPoint.first - 40).dp,
                                            y = (focusPoint.second - 40).dp,
                                        )
                                        .size(80.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .padding(8.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(
                                                Color.White.copy(alpha = 0.8f),
                                                shape = CircleShape,
                                            )
                                            .padding(4.dp),
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(
                                                    Color.Transparent,
                                                    shape = CircleShape,
                                                )
                                                .clip(CircleShape),
                                        ) {
                                            // Focus ring animation
                                            repeat(2) { index ->
                                                val delay = index * 200
                                                val animatedScale by animateFloatAsState(
                                                    targetValue = if (showFocusIndicator) 1f else 0f,
                                                    animationSpec = infiniteRepeatable(
                                                        animation = keyframes {
                                                            durationMillis = 1000
                                                            0f at delay
                                                            1f at (delay + 400)
                                                            0f at 1000
                                                        },
                                                    ),
                                                )
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .scale(animatedScale)
                                                        .background(
                                                            Color.White.copy(alpha = 0.5f),
                                                            shape = CircleShape,
                                                        ),
                                                )
                                            }
                                        }
                                    }
                                }

                                // Hide focus indicator after 2 seconds
                                LaunchedEffect(showFocusIndicator) {
                                    if (showFocusIndicator) {
                                        delay(2000)
                                        showFocusIndicator = false
                                    }
                                }
                            }
                        }
                    } else {
                        // Enhanced permission request UI
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black),
                            contentAlignment = Alignment.Center,
                        ) {
                            Surface(
                                modifier = Modifier.padding(32.dp),
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White,
                                shadowElevation = 8.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Camera,
                                        contentDescription = "Camera",
                                        tint = Color.Gray,
                                        modifier = Modifier.size(64.dp),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(
                                        text = "Camera Access Required",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Please grant camera permission to use this feature",
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    )
                                    Spacer(modifier = Modifier.height(24.dp))
                                    Button(
                                        onClick = {
                                            launcher.launch(Manifest.permission.CAMERA)
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        Text("Grant Permission")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } // End of Box
    } else {
// Enhanced permission request UI
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier = Modifier.padding(32.dp),
                shape = RoundedCornerShape(16.dp),
                color = Color.White,
                shadowElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(
                        imageVector = Icons.Default.Camera,
                        contentDescription = "Camera",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp),
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Camera Access Required",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please grant camera permission to use this feature",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            launcher.launch(Manifest.permission.CAMERA)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Grant Permission")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.9f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
    )

    IconButton(
        onClick = onClick,
        modifier = modifier
            .size(40.dp)
            .scale(scale)
            .background(
                Color.White.copy(alpha = 0.2f),
                shape = CircleShape,
            ),
        interactionSource = remember { MutableInteractionSource() },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}
