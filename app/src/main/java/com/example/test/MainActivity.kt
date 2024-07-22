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
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import java.io.File
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import com.example.test.ui.theme.buttonTextStyle
import com.example.test.ui.theme.buttonTextStyle

import androidx.activity.compose.setContent
import com.example.test.ui.theme.TestTheme
import androidx.activity.compose.setContent
import com.example.test.ui.theme.TestTheme

class MainActivity : ComponentActivity() {
    private lateinit var mediaProjectionManager: MediaProjectionManager
    private var mediaRecorder: MediaRecorder? = null
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private lateinit var screenCaptureLauncher: ActivityResultLauncher<Intent>
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>  // Updated to single permission request
    private var recordingStartTime: Long = 0
    private val RECORDING_LIMIT = 180000L
    private val recordingHandler = Handler(Looper.getMainLooper())
    private val restartRecordingRunnable = Runnable { restartRecording() }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize the permission launcher for RECORD_AUDIO
        requestPermissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                if (!isGranted) {
                    Log.e("Permissions", "Permission not granted for recording audio.")
                }
            }

        // Request RECORD_AUDIO permission
        requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)


        // Initialize mediaProjectionManager
        mediaProjectionManager =
            getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager

        // Setup screen capture intent response handling
        screenCaptureLauncher =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == RESULT_OK && result.data != null) {
                    mediaProjection =
                        mediaProjectionManager.getMediaProjection(result.resultCode, result.data!!)
                            .apply {
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
                    Log.e(
                        "ScreenCapture",
                        "Permission not granted or failed to retrieve projection."
                    )
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
                            val screenCaptureIntent =
                                mediaProjectionManager.createScreenCaptureIntent()
                            screenCaptureLauncher.launch(screenCaptureIntent)
                        },
                        onStopRecording = { stopRecording() },
                        onOpenLink = { openLink() }
                    )
                }
            }
        }
    }

    private fun setupMediaRecorder() {
        val dateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
        val fileName = "screen_record_${dateFormat.format(Date())}.mp4"
        val storageDir = getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath + "/ScreenRecordings"

        File(storageDir).mkdirs()  // 确保目录存在

        val filePath = "$storageDir/$fileName"

        mediaRecorder = MediaRecorder().apply {
            setOnErrorListener { _, what, extra ->
                Log.e("MediaRecorder", "Error occurred: What $what, Extra $extra")
                stopRecording()
            }
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(filePath)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setVideoSize(1280, 720)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5000000)
            prepare()
        }
    }


    private fun startRecording() {
        setupMediaRecorder()
        if (virtualDisplay == null) {
            val metrics = resources.displayMetrics
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MainActivity",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface, null, null
            )
        }
        mediaRecorder?.start()
        recordingStartTime = System.currentTimeMillis()
        Log.d("Recording", "Recording started.")
        Handler(Looper.getMainLooper()).postDelayed({
            if (System.currentTimeMillis() - recordingStartTime >= RECORDING_LIMIT) {
                restartRecording()
            }
        }, RECORDING_LIMIT)
    }


    private fun stopRecording() {
        Log.d("Recording", "Stopping the recording.")
        mediaRecorder?.apply {
            stop()
            reset()
            release()
        }
        mediaRecorder = null
        if (virtualDisplay != null) {
            virtualDisplay?.release()
            virtualDisplay = null
        }
        mediaProjection?.stop()
        mediaProjection = null
        Log.d("Recording", "Recording stopped.")
    }

    private fun restartRecording() {
        mediaRecorder?.apply {
            stop()
            reset()
            release()
        }  // Stop without releasing the virtual display
        setupMediaRecorder() // 重新设置 MediaRecorder

        val metrics = resources.displayMetrics
        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "MainActivity",
                metrics.widthPixels, metrics.heightPixels, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface, null, null
            )
        } else {
            virtualDisplay?.surface = mediaRecorder!!.surface
        }

        mediaRecorder?.start()
        recordingHandler.postDelayed({
            if (System.currentTimeMillis() - recordingStartTime >= RECORDING_LIMIT) {
                restartRecording()
            }
        }, RECORDING_LIMIT)
// 重新启动录制
    }

    @Composable
    fun Greeting(
        name: String,
        modifier: Modifier,
        onStartRecording: () -> Unit,
        onStopRecording: () -> Unit,
        onOpenLink: () -> Unit
    ) {
        Column(
            modifier = modifier,
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Hello $name!")
            Button(onClick = onStartRecording) {
                Text("开始录屏",style = buttonTextStyle)
            }
            Button(onClick = {
                onStopRecording()
                virtualDisplay?.release()
                virtualDisplay = null
            }) {
                Text("停止录屏",style = buttonTextStyle)
            }
            Button(onClick = onOpenLink) {  // 添加一个新的按钮用于打开链接
                Text("打开链接", style = buttonTextStyle)
            }
        }
    }
    private fun openLink() {
        val url = "https://zhuayumao.cn/Download"  // 将 URL 替换为你想要打开的链接
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        startActivity(intent)
    }





    @Preview(showBackground = true)
    @Composable
    fun GreetingPreview() {
        TestTheme {
            Greeting("Android", Modifier.fillMaxSize(), {}, {},{})
        }
    }
}