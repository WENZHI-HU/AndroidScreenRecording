package com.example.test
import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.os.Bundle
import android.app.Service
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.example.test.ui.theme.TestTheme
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Environment
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>  // Updated to single permission request

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the permission launcher for RECORD_AUDIO
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (!isGranted) {
                Log.e("Permissions", "Permission not granted for recording audio.")
            }
        }

        // Request RECORD_AUDIO permission
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)

        // Initialize mediaProjectionManager
        mediaProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Setup screen capture intent response handling
        screenCaptureLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK && result.data != null) {
                mediaProjection = mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!).apply {
                    // Register a callback to handle user revocation and cleanup
                    registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            stopRecording()
                            mediaProjection = null
                        }
                    }, null)
                }
                startRecording() // Start recording now that everything is set up
            } else {
                Log.e("ScreenCapture", "Permission not granted or failed to retrieve projection.")
            }
        }


        setContent {
            TestTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Greeting("Android", Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                        onStartRecording = {
                            val serviceIntent = Intent(this, RecordingService::class.java)
                            ContextCompat.startForegroundService(this, serviceIntent)
                            val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
                            screenCaptureLauncher.launch(screenCaptureIntent)
                        },
                        onStopRecording = { stopRecording() }
                    )
                }
            }
        }
    }

    private fun setupMediaRecorder() {
        val resolver = contentResolver
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "screen_record_${System.currentTimeMillis()}.mp4")
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/ScreenRecordings")
        }

        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)

        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(uri?.let { resolver.openFileDescriptor(it, "rw")?.fileDescriptor })
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(1280, 720)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5000000)
            prepare()
        }
    }

    private fun startRecording() {
        Log.d("Recording", "Preparing to start recording.")
        setupMediaRecorder()
        val metrics = resources.displayMetrics
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "MainActivity",
            metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder!!.surface, null, null
        )
        mediaRecorder?.start()
        Log.d("Recording", "Recording started.")
    }

    private fun stopRecording() {
        Log.d("Recording", "Stopping the recording.")
        mediaRecorder?.apply {
            stop()
            reset()
            release()
        }
        mediaRecorder = null
        virtualDisplay?.release()
        virtualDisplay = null
        mediaProjection?.stop()
        mediaProjection = null
        Log.d("Recording", "Recording stopped.")
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier, onStartRecording: () -> Unit, onStopRecording: () -> Unit) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = "Hello $name!")
        Button(onClick = onStartRecording) {
            Text("开始录屏")
        }
        Button(onClick = onStopRecording) {
            Text("停止录屏")
        }
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    TestTheme {
        Greeting("Android", Modifier.fillMaxSize(), {}, {})
    }
}
