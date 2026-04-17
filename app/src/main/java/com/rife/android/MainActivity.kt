package com.rife.android

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                    dynamicDarkColorScheme(LocalContext.current)
                } else {
                    darkColorScheme()
                }
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    RifeApp()
                }
            }
        }
    }
}

suspend fun downloadFile(urlStr: String, destinationFile: File, onProgress: (Float) -> Unit) {
    withContext(Dispatchers.IO) {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection
        connection.connect()

        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw Exception("Server returned HTTP ${connection.responseCode}")
        }

        val fileLength = connection.contentLength
        val input = BufferedInputStream(connection.inputStream)
        val output = FileOutputStream(destinationFile)

        val data = ByteArray(8192)
        var total: Long = 0
        var count: Int
        while (input.read(data).also { count = it } != -1) {
            total += count.toLong()
            if (fileLength > 0) {
                onProgress(total.toFloat() / fileLength.toFloat())
            }
            output.write(data, 0, count)
        }

        output.flush()
        output.close()
        input.close()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RifeApp() {
    val context = LocalContext.current
    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    var processing by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Select a video to start") }
    var downloadProgress by remember { mutableStateOf(0f) }

    val modelDir = File(context.filesDir, "rife-v4.6")
    val modelBin = File(modelDir, "rife-v4.6.bin")
    val modelParam = File(modelDir, "rife-v4.6.param")
    
    var modelsExist by remember { mutableStateOf(modelBin.exists() && modelParam.exists()) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Rife Android") }) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = statusMessage, modifier = Modifier.padding(16.dp))

            if (!modelsExist) {
                if (downloading) {
                    LinearProgressIndicator(progress = downloadProgress, modifier = Modifier.fillMaxWidth().padding(16.dp))
                    Text("Downloading models... ${(downloadProgress * 100).toInt()}%")
                } else {
                    Button(onClick = {
                        downloading = true
                        statusMessage = "Downloading models..."
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                if (!modelDir.exists()) modelDir.mkdirs()
                                val baseUrl = "https://raw.githubusercontent.com/nihui/rife-ncnn-vulkan/master/models/rife-v4.6/"
                                downloadFile(baseUrl + "flownet.bin", modelBin) { downloadProgress = it / 2f }
                                downloadFile(baseUrl + "flownet.param", modelParam) { downloadProgress = 0.5f + (it / 2f) }
                                withContext(Dispatchers.Main) {
                                    modelsExist = true
                                    downloading = false
                                    statusMessage = "Models downloaded! Select a video."
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    statusMessage = "Download failed: ${e.message}"
                                    downloading = false
                                }
                            }
                        }
                    }) {
                        Text("Download Models (11 MB)")
                    }
                }
            } else {
                Button(onClick = { launcher.launch("video/*") }, enabled = !processing) {
                    Text("Select Video")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        selectedVideoUri?.let { uri ->
                            processing = true
                            statusMessage = "Preparing..."

                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val inputStream = context.contentResolver.openInputStream(uri)
                                    val tempFile = File(context.cacheDir, "input_video.mp4")
                                    val outputFile = File(context.getExternalFilesDir(null), "output_60fps.mp4")
                                    
                                    inputStream?.use { input ->
                                        FileOutputStream(tempFile).use { output ->
                                            input.copyTo(output)
                                        }
                                    }

                                    val framesDir = File(context.cacheDir, "frames")
                                    val outFramesDir = File(context.cacheDir, "out_frames")
                                    framesDir.deleteRecursively(); outFramesDir.deleteRecursively()
                                    framesDir.mkdirs(); outFramesDir.mkdirs()

                                    withContext(Dispatchers.Main) { statusMessage = "Extracting frames..." }
                                    val extractSession = FFmpegKit.execute("-i ${tempFile.absolutePath} -qscale:v 1 ${framesDir.absolutePath}/frame_%08d.png")
                                    if (!ReturnCode.isSuccess(extractSession.returnCode)) throw Exception("Extraction failed")

                                    val frames = framesDir.listFiles()?.sortedBy { it.name } ?: emptyList()
                                    if (frames.isEmpty()) throw Exception("No frames")

                                    withContext(Dispatchers.Main) { statusMessage = "Interpolating..." }
                                    val engine = RifeEngine()
                                    if (engine.initEngine(modelDir.absolutePath) != 0) throw Exception("Engine init failed")

                                    for (i in frames.indices) {
                                        withContext(Dispatchers.Main) { statusMessage = "Processing ${i+1}/${frames.size}" }
                                        val outP = File(outFramesDir, String.format("out_%08d.png", i * 2 + 1))
                                        frames[i].copyTo(outP)
                                        if (i < frames.size - 1) {
                                            val interpP = File(outFramesDir, String.format("out_%08d.png", i * 2 + 2))
                                            engine.processFrame(frames[i].absolutePath, frames[i+1].absolutePath, interpP.absolutePath)
                                        }
                                    }
                                    engine.destroyEngine()

                                    withContext(Dispatchers.Main) { statusMessage = "Encoding..." }
                                    val encodeCmd = "-framerate 60 -i ${outFramesDir.absolutePath}/out_%08d.png -i ${tempFile.absolutePath} -map 0:v:0 -map 1:a:0? -c:v mpeg4 -qscale:v 1 -c:a copy -y ${outputFile.absolutePath}"
                                    FFmpegKit.execute(encodeCmd)

                                    withContext(Dispatchers.Main) { statusMessage = "Done! Saved to: ${outputFile.name}" }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { statusMessage = "Error: ${e.message}" }
                                } finally {
                                    processing = false
                                }
                            }
                        }
                    },
                    enabled = selectedVideoUri != null && !processing
                ) {
                    Text("Convert to 60fps")
                }
            }

            if (processing) {
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            }
        }
    }
}
