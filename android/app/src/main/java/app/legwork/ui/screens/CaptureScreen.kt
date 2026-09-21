package app.legwork.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.legwork.core.Config
import app.legwork.ui.Routes
import app.legwork.ui.components.PrimaryButton
import app.legwork.ui.components.SecondaryButton
import app.legwork.ui.theme.Legwork
import app.legwork.vm.AppViewModel
import java.io.ByteArrayOutputStream

@Composable
fun CaptureScreen(vm: AppViewModel, nav: NavHostController) {
    val state by vm.state.collectAsStateWithLifecycle()
    val ctx = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val m = state.activeMission
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) { if (!granted) permission.launch(Manifest.permission.CAMERA) }
    var photo by remember { mutableStateOf<ByteArray?>(null) }
    var bitmap by remember { mutableStateOf<Bitmap?>(null) }
    var capturing by remember { mutableStateOf(false) }
    val capture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(ResolutionStrategy(android.util.Size(1280, 960), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER))
                    .build()
            )
            .build()
    }
    if (m == null) { LaunchedEffect(Unit) { nav.popBackStack() }; return }
    val fix = state.fix
    val arrived = fix != null && (vm.distanceTo(m) ?: Double.MAX_VALUE) <= m.radiusM + Config.ARRIVAL_SLACK_M + fix.accuracyM.coerceAtMost(30f)

    Column(Modifier.fillMaxSize().background(Legwork.Ink)) {
        Box(Modifier.weight(1f)) {
            if (bitmap != null) {
                Image(bitmap!!.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else if (granted) {
                AndroidView(factory = { c ->
                    PreviewView(c).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        val future = ProcessCameraProvider.getInstance(c)
                        future.addListener({
                            val provider = future.get()
                            val preview = Preview.Builder().build().also { it.surfaceProvider = surfaceProvider }
                            provider.unbindAll()
                            runCatching { provider.bindToLifecycle(lifecycle, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture) }
                        }, ContextCompat.getMainExecutor(c))
                    }
                }, modifier = Modifier.fillMaxSize())
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission is needed to capture proof", color = Color.White)
                }
            }
            Row(Modifier.statusBarsPadding().padding(8.dp)) {
                IconButton(onClick = { if (bitmap != null) { bitmap = null; photo = null } else nav.popBackStack() },
                    modifier = Modifier.clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))) {
                    Icon(Icons.Filled.ArrowBack, "Back", tint = Color.White)
                }
            }
            // Framing hint for the mission
            Column(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 60.dp, start = 24.dp, end = 24.dp)) {
                Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Color.Black.copy(alpha = 0.55f)).padding(12.dp)) {
                    Text(m.instructions, color = Color.White, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (bitmap == null) Box(
                Modifier.align(Alignment.Center).size(260.dp, 200.dp)
                    .border(2.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
            )
        }
        Column(Modifier.background(Legwork.Surface).navigationBarsPadding().padding(20.dp)) {
            Checklist(
                listOf(
                    "At location" to arrived,
                    "GPS verified ${fix?.accuracyM?.toInt()?.let { "(±$it m)" } ?: ""}" to (fix != null && fix.accuracyM <= 60f),
                    "Photo captured" to (photo != null),
                )
            )
            Spacer(Modifier.height(14.dp))
            if (photo == null) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).border(4.dp, Legwork.Line, CircleShape).padding(6.dp)
                            .clip(CircleShape).background(if (capturing) Legwork.Muted else Legwork.Accent)
                            .clickable(enabled = granted && !capturing) {
                                capturing = true
                                capture.takePicture(ContextCompat.getMainExecutor(ctx), object : ImageCapture.OnImageCapturedCallback() {
                                    override fun onCaptureSuccess(image: ImageProxy) {
                                        val bytes = image.use { encode(it) }
                                        photo = bytes.first; bitmap = bytes.second; capturing = false
                                    }
                                    override fun onError(exception: ImageCaptureException) {
                                        capturing = false; vm.toast("Camera error: ${exception.message}")
                                    }
                                })
                            }
                    )
                }
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SecondaryButton("Retake", modifier = Modifier.weight(1f), onClick = { photo = null; bitmap = null })
                    PrimaryButton("Submit proof", modifier = Modifier.weight(1f), color = Legwork.Money, enabled = fix != null, onClick = {
                        val f = fix ?: return@PrimaryButton
                        vm.submitProof(m, photo!!, f) { }
                        nav.navigate(Routes.RESULT) { popUpTo(Routes.HOME) }
                    })
                }
            }
        }
    }
}

/** JPEG bytes at a bounded size plus a bitmap for preview, rotated upright. */
private fun encode(image: ImageProxy): Pair<ByteArray, Bitmap> {
    val buf = image.planes[0].buffer
    val raw = ByteArray(buf.remaining()).also { buf.get(it) }
    val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
    var bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size, opts)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation != 0) {
        val mtx = Matrix().apply { postRotate(rotation.toFloat()) }
        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mtx, true)
    }
    val maxSide = 1280
    if (bmp.width > maxSide || bmp.height > maxSide) {
        val s = maxSide.toFloat() / maxOf(bmp.width, bmp.height)
        bmp = Bitmap.createScaledBitmap(bmp, (bmp.width * s).toInt(), (bmp.height * s).toInt(), true)
    }
    val out = ByteArrayOutputStream()
    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
    return out.toByteArray() to bmp
}
