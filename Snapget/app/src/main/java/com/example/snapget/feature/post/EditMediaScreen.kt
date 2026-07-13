package com.example.snapget.feature.post

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.snapget.core.designsystem.component.common.CommonTopBar
import com.example.snapget.core.designsystem.theme.SnapYellow
import com.example.snapget.core.network.dto.FrameDto
import com.example.snapget.navigation.Screen
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Vang selection/capture theo DESIGN.md (accent duy nhat)

/** 1 lua chon filter mau — matrix null = khong filter. */
data class FilterOption(val name: String, val matrix: ColorMatrix?)

/** Bo filter co dinh (ColorMatrix 4x5). Bake vao anh khi bam Tiep. */
val PHOTO_FILTERS: List<FilterOption> = listOf(
    FilterOption("Normal", null),
    FilterOption("Mono", ColorMatrix().apply { setToSaturation(0f) }),
    FilterOption(
        "Sepia",
        ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    ),
    FilterOption(
        "Warm",
        ColorMatrix(
            floatArrayOf(
                1.1f, 0f, 0f, 0f, 15f,
                0f, 1.03f, 0f, 0f, 5f,
                0f, 0f, 0.9f, 0f, -10f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    ),
    FilterOption(
        "Cool",
        ColorMatrix(
            floatArrayOf(
                0.9f, 0f, 0f, 0f, -10f,
                0f, 1.0f, 0f, 0f, 0f,
                0f, 0f, 1.1f, 0f, 15f,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    ),
    FilterOption("Vivid", ColorMatrix().apply { setToSaturation(1.5f) }),
)

/** 1 net ve tay tren anh (toa do px theo khung preview vuong). */
data class DoodleStroke(
    val color: Color,
    val strokeWidthPx: Float,
    val points: List<Offset>,
)

private val DOODLE_COLORS = listOf(
    Color.White,
    Color.Black,
    Color.Red,
    Color.Yellow,
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFFE040FB),
)

/** Do day net ve (px tren preview): nho / vua / to. */
private val DOODLE_WIDTHS = listOf(8f, 12f, 20f)

/**
 * Man chinh sua SAU KHI CHUP (user chot 2026-07-13): giong submit_photo_screen.png nhung
 * nut TIEP thay nut dang, picker khung + filter nam DUOI anh, KHONG co o caption.
 * Anh: filter + doodle duoc BAKE vao file moi; khung gui kem frameId (overlay khi hien thi).
 * Video: chi chon khung (khong filter/doodle).
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun EditMediaScreen(
    navController: NavController,
    mediaPath: String,
    isVideo: Boolean = false,
    postViewModel: PostViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Catalog khung (chi khung DA MO KHOA duoc chon)
    val frames by postViewModel.frames.collectAsState()
    LaunchedEffect(Unit) { postViewModel.loadFrames() }
    val unlockedFrames = remember(frames) { frames.filter { it.isUnlocked } }

    var selectedFilter by remember { mutableStateOf(PHOTO_FILTERS.first()) }
    var selectedFrame by remember { mutableStateOf<FrameDto?>(null) }

    // Doodle state
    var doodleMode by remember { mutableStateOf(false) }
    var doodleColor by remember { mutableStateOf(Color.White) }
    var doodleWidth by remember { mutableStateOf(12f) }
    var strokes by remember { mutableStateOf(listOf<DoodleStroke>()) }
    var currentPoints by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var previewSizePx by remember { mutableStateOf(0) }

    var isBaking by remember { mutableStateOf(false) }

    // Bitmap goc (chi anh) — decode o IO thread (anh do phan giai cao decode + xoay
    // EXIF mat hang tram ms; de o main thread nhu truoc lam dung hinh khi mo man)
    val sourceBitmap by produceState<Bitmap?>(initialValue = null, mediaPath) {
        value = if (isVideo) null else withContext(Dispatchers.IO) { decodeUprightBitmap(mediaPath) }
    }

    /** Bake filter + doodle vao file moi roi sang man SubmitPhoto. */
    fun goNext() {
        if (isBaking) return
        val bitmap = sourceBitmap
        if (!isVideo && bitmap == null) return // anh dang decode — bam Tiep sau 1 nhip
        coroutineScope.launch {
            isBaking = true
            try {
                val finalPath = if (isVideo || bitmap == null) {
                    mediaPath // video khong bake
                } else {
                    withContext(Dispatchers.IO) {
                        bakeImage(
                            context.cacheDir,
                            bitmap,
                            selectedFilter.matrix,
                            strokes,
                            previewSizePx,
                        )
                    }
                }
                // Gui kem frameUrl de SubmitPhoto overlay ngay — khong phai tai lai
                // catalog /frames chi de resolve 1 URL (2 ViewModel khac nhau khong
                // chia se state; truoc day /frames loi la preview mat khung im lang)
                val frame = selectedFrame
                val frameParam = frame?.frameId?.let { id ->
                    "&frameId=" + Uri.encode(id) +
                        (frame.imageUrl?.let { url -> "&frameUrl=" + Uri.encode(url) } ?: "")
                } ?: ""
                navController.navigate(
                    Screen.SubmitPhoto.route +
                        "?photoPath=" + Uri.encode(finalPath) +
                        "&isVideo=" + isVideo +
                        frameParam,
                )
            } catch (e: Exception) {
                Toast.makeText(context, "Xu ly anh that bai: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                isBaking = false
            }
        }
    }

    Scaffold(
        topBar = {
            CommonTopBar(
                navController = navController,
                title = "Edit",
            )
        },
    ) { paddingValues ->
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            // ==== Preview vuong bo 20dp: anh (filter) + khung + doodle ====
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color.Black)
                    .onGloballyPositioned { previewSizePx = it.size.width },
            ) {
                val bitmap = sourceBitmap
                when {
                    bitmap != null -> Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Anh vua chup",
                        contentScale = ContentScale.Crop,
                        colorFilter = selectedFilter.matrix?.let { ColorFilter.colorMatrix(it) },
                        modifier = Modifier.fillMaxSize(),
                    )

                    isVideo ->
                        // Video: preview don gian (icon play) — video se phat that o feed
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier.size(36.dp),
                                )
                            }
                        }

                    else ->
                        // Anh dang decode o IO thread
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = Color.White)
                        }
                }

                // Khung overlay (PNG trong suot phu kin anh)
                selectedFrame?.imageUrl?.let { frameUrl ->
                    AsyncImage(
                        model = frameUrl,
                        contentDescription = selectedFrame?.frameName,
                        contentScale = ContentScale.FillBounds,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                // Lop doodle: ve khi doodleMode bat; luon hien cac net da ve
                if (!isVideo) {
                    androidx.compose.foundation.Canvas(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (doodleMode) {
                                    Modifier.pointerInput(doodleColor) {
                                        detectDragGestures(
                                            onDragStart = { offset ->
                                                currentPoints = listOf(offset)
                                            },
                                            onDrag = { change, _ ->
                                                currentPoints = currentPoints + change.position
                                            },
                                            onDragEnd = {
                                                if (currentPoints.size > 1) {
                                                    strokes = strokes + DoodleStroke(
                                                        color = doodleColor,
                                                        strokeWidthPx = doodleWidth,
                                                        points = currentPoints,
                                                    )
                                                }
                                                currentPoints = emptyList()
                                            },
                                        )
                                    }
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        (
                            strokes + listOfNotNull(
                                currentPoints.takeIf { it.size > 1 }?.let {
                                    DoodleStroke(doodleColor, doodleWidth, it)
                                },
                            )
                            ).forEach { stroke ->
                            val path = androidx.compose.ui.graphics.Path().apply {
                                moveTo(stroke.points.first().x, stroke.points.first().y)
                                stroke.points.drop(1).forEach { lineTo(it.x, it.y) }
                            }
                            drawPath(
                                path = path,
                                color = stroke.color,
                                style = Stroke(
                                    width = stroke.strokeWidthPx,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                ),
                            )
                        }
                    }
                }

                // Overlay loading khi dang bake anh
                if (isBaking) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // ==== Thanh cong cu doodle (chi anh) ====
            if (!isVideo) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                ) {
                    // Nut bat/tat ve tay
                    Surface(
                        shape = CircleShape,
                        color = Color(0xFF404137),
                        modifier = Modifier
                            .size(40.dp)
                            .then(
                                if (doodleMode) {
                                    Modifier.border(2.dp, SnapYellow, CircleShape)
                                } else {
                                    Modifier
                                },
                            ),
                    ) {
                        IconButton(onClick = { doodleMode = !doodleMode }) {
                            Icon(
                                imageVector = Icons.Filled.Edit,
                                contentDescription = "Ve tay",
                                tint = if (doodleMode) SnapYellow else Color.White,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    // Bang mau khi dang ve
                    if (doodleMode) {
                        DOODLE_COLORS.forEach { color ->
                            Box(
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .border(
                                        width = if (doodleColor == color) 3.dp else 1.dp,
                                        color = if (doodleColor == color) {
                                            SnapYellow
                                        } else {
                                            Color.White.copy(alpha = 0.4f)
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { doodleColor = color },
                            )
                        }
                        // Chon do day net: cham nho / vua / to
                        DOODLE_WIDTHS.forEach { width ->
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (doodleWidth == width) 2.dp else 1.dp,
                                        color = if (doodleWidth == width) {
                                            SnapYellow
                                        } else {
                                            Color.White.copy(alpha = 0.4f)
                                        },
                                        shape = CircleShape,
                                    )
                                    .clickable { doodleWidth = width },
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size((width / 2).dp)
                                        .clip(CircleShape)
                                        .background(Color.White),
                                )
                            }
                        }
                        IconButton(
                            onClick = { strokes = strokes.dropLast(1) },
                            enabled = strokes.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Undo,
                                contentDescription = "Hoan tac net ve",
                                tint = if (strokes.isNotEmpty()) Color.White else Color.Gray,
                            )
                        }
                        IconButton(
                            onClick = { strokes = emptyList() },
                            enabled = strokes.isNotEmpty(),
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = "Xoa het net ve",
                                tint = if (strokes.isNotEmpty()) Color.White else Color.Gray,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // ==== Picker FILTER (chi anh) ====
                SectionLabel("Filters")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(PHOTO_FILTERS) { filter ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .border(
                                        width = if (selectedFilter == filter) 2.dp else 0.dp,
                                        color = if (selectedFilter == filter) {
                                            SnapYellow
                                        } else {
                                            Color.Transparent
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                    )
                                    .clickable { selectedFilter = filter },
                            ) {
                                sourceBitmap?.let { thumb ->
                                    Image(
                                        bitmap = thumb.asImageBitmap(),
                                        contentDescription = filter.name,
                                        contentScale = ContentScale.Crop,
                                        colorFilter = filter.matrix?.let {
                                            ColorFilter.colorMatrix(it)
                                        },
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                            Text(
                                text = filter.name,
                                color = if (selectedFilter == filter) SnapYellow else Color.White,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // ==== Picker KHUNG ====
            SectionLabel("Frames")
            if (unlockedFrames.isEmpty()) {
                Text(
                    text = "Complete daily quests to unlock frames!",
                    color = Color(0xFFB0B0B0),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    // Lua chon "khong khung" dau tien
                    item {
                        FrameThumb(
                            label = "None",
                            imageUrl = null,
                            selected = selectedFrame == null,
                            onClick = { selectedFrame = null },
                        )
                    }
                    items(unlockedFrames) { frame ->
                        FrameThumb(
                            label = frame.frameName,
                            imageUrl = frame.imageUrl,
                            selected = selectedFrame?.frameId == frame.frameId,
                            onClick = { selectedFrame = frame },
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ==== Bottom: ✕ Huy · nut TIEP 80dp vien vang ====
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Huy",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp),
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.9f),
                    modifier = Modifier
                        .size(80.dp)
                        .border(3.dp, SnapYellow, CircleShape),
                ) {
                    IconButton(onClick = { goNext() }, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Tiep",
                            tint = Color.Black,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.width(48.dp)) // can giua nut Tiep
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = Color.White,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
    )
}

@Composable
private fun FrameThumb(
    label: String,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF2C2C2C))
                .border(
                    width = if (selected) 2.dp else 0.dp,
                    color = if (selected) SnapYellow else Color.Transparent,
                    shape = RoundedCornerShape(14.dp),
                )
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = label,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp),
                )
            } else {
                Text(text = "∅", color = Color.White, fontSize = 20.sp)
            }
        }
        Text(
            text = label,
            color = if (selected) SnapYellow else Color.White,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier
                .width(64.dp)
                .padding(top = 2.dp),
        )
    }
}

/** Decode anh + xoay dung chieu theo EXIF (Coil tu lam, con Bitmap thi phai tu xoay). */
private fun decodeUprightBitmap(path: String): Bitmap? {
    // Doc bounds truoc de tinh inSampleSize — anh 12MP giu full-size ton ~48MB
    // (x2 khi xoay EXIF); preview + bake chi can toi da ~2048px canh dai
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
    val options = BitmapFactory.Options().apply {
        inSampleSize = 1
        while (maxDim / inSampleSize > 2048) inSampleSize *= 2
    }
    val bitmap = BitmapFactory.decodeFile(path, options) ?: return null
    return try {
        val exif = androidx.exifinterface.media.ExifInterface(path)
        val rotation = when (
            exif.getAttributeInt(
                androidx.exifinterface.media.ExifInterface.TAG_ORIENTATION,
                androidx.exifinterface.media.ExifInterface.ORIENTATION_NORMAL,
            )
        ) {
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            androidx.exifinterface.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) {
            bitmap
        } else {
            val matrix = android.graphics.Matrix().apply { postRotate(rotation) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        }
    } catch (_: Exception) {
        bitmap
    }
}

/**
 * Bake filter + doodle vao anh: crop vuong giua (khop preview ContentScale.Crop),
 * ap ColorMatrix, ve cac net doodle (scale tu toa do preview sang toa do anh).
 */
private fun bakeImage(
    cacheDir: File,
    source: Bitmap,
    filterMatrix: ColorMatrix?,
    strokes: List<DoodleStroke>,
    previewSizePx: Int,
): String {
    val squareSize = min(source.width, source.height)
    val x = (source.width - squareSize) / 2
    val y = (source.height - squareSize) / 2
    val square = Bitmap.createBitmap(source, x, y, squareSize, squareSize)

    val output = Bitmap.createBitmap(squareSize, squareSize, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(output)

    val photoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        filterMatrix?.let {
            colorFilter = android.graphics.ColorMatrixColorFilter(
                android.graphics.ColorMatrix(it.values),
            )
        }
    }
    canvas.drawBitmap(square, 0f, 0f, photoPaint)

    // Doodle: toa do dang theo khung preview -> scale ve kich thuoc anh that
    if (strokes.isNotEmpty() && previewSizePx > 0) {
        val scale = squareSize.toFloat() / previewSizePx
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        strokes.forEach { stroke ->
            strokePaint.color = stroke.color.toArgb()
            strokePaint.strokeWidth = stroke.strokeWidthPx * scale
            val path = Path().apply {
                moveTo(stroke.points.first().x * scale, stroke.points.first().y * scale)
                stroke.points.drop(1).forEach { lineTo(it.x * scale, it.y * scale) }
            }
            canvas.drawPath(path, strokePaint)
        }
    }

    val outFile = File(cacheDir, "snapget_edit_${System.currentTimeMillis()}.jpg")
    FileOutputStream(outFile).use { stream ->
        output.compress(Bitmap.CompressFormat.JPEG, 92, stream)
    }
    return outFile.absolutePath
}
