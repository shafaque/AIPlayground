package com.shaf.aiplayground

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shaf.aiplayground.ui.theme.AIPlaygroundTheme
import dev.jeziellago.compose.markdowntext.MarkdownText

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AIPlaygroundTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(mainViewModel: MainViewModel = viewModel()) {
    val uiState by mainViewModel.uiState.collectAsState()
    var hasCameraPermission by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        hasCameraPermission = isGranted
    }

    LaunchedEffect(Unit) {
        val permissionStatus = ContextCompat.checkSelfPermission(
            context, Manifest.permission.CAMERA
        )
        if (permissionStatus == PackageManager.PERMISSION_GRANTED) {
            hasCameraPermission = true
        } else {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI Playground") },
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                if (hasCameraPermission) {
                    CameraPreview(
                        updateBitmap = { mainViewModel.setBitmap(it) },
                        onClick = { mainViewModel.askGemini() }
                    )
                }

                // Show content based on UI state
                when (uiState) {
                    is UiState.Success -> {
                        val outputText = (uiState as UiState.Success).outputText
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xA0000000))
                                .padding(16.dp)
                        ) {
                            MarkdownText(
                                markdown = outputText,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color.White)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = { mainViewModel.reset() },
                                modifier = Modifier
                                    .align(Alignment.End)
                            ) {
                                Text(text = stringResource(id = R.string.done))
                            }
                        }
                    }
                    is UiState.Error -> {
                        val errorMessage = (uiState as UiState.Error).errorMessage
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                                .background(Color(0xAAFFFFFF))
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyLarge.copy(color = Color.Red),
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(16.dp)
                            )
                        }
                    }
                    is UiState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(16.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    else -> Unit // No UI state to handle
                }
            }
        }
    )
}


@Composable
fun CameraPreview(updateBitmap: (Bitmap?) -> Unit, onClick: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .clickable { onClick() },
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val executor = ContextCompat.getMainExecutor(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().apply {
                    setSurfaceProvider(previewView.surfaceProvider)
                }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .apply {
                        setAnalyzer(executor) { imageProxy ->
                            try {
                                updateBitmap(imageProxy.toBitmap())
                            } catch (e: Exception) {
                                // Handle exceptions, e.g., log the error
                            } finally {
                                imageProxy.close()
                            }
                        }
                    }

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                    )
                } catch (e: Exception) {
                    // Handle exceptions, e.g., log the error
                }
            }, executor)
            previewView
        }
    )
}
