package com.scootcheck.app

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class PendingCapture(val video: File, val frame: File, val source: String)

@Composable
fun CaptureScreen(store: InspectionStore, rentalId: Long, kind: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycle = LocalLifecycleOwner.current
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(store.media(rentalId, kind)) }
    val face = (0..3).firstOrNull { index -> saved.none { it.face == index } }
    var permitted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permitted = it }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<PendingCapture?>(null) }
    var capture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    val previewView = remember { PreviewView(context).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val scroll = rememberScrollState()
    LaunchedEffect(face, pending) { scroll.scrollTo(0) }
    fun prepare(file: File, source: String) {
        busy = true; error = null
        scope.launch {
            val output = File(file.parentFile, file.nameWithoutExtension + ".jpg")
            try {
                withContext(Dispatchers.IO) { VideoFrames.extract(file, output) }
                pending = PendingCapture(file, output, source)
            } catch (e: Exception) {
                file.delete(); output.delete(); File(output.absolutePath + ".json").delete()
                error = e.message ?: "Vidéo illisible. Reprenez cette face."
            } finally { busy = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            busy = true; error = null
            scope.launch {
                val file = store.newVideo()
                try {
                    withContext(Dispatchers.IO) {
                        check(context.filesDir.usableSpace > 300L*1024*1024) { "Espace insuffisant. Libérez au moins 300 Mo." }
                        context.contentResolver.openInputStream(uri).use { input ->
                            checkNotNull(input) { "Impossible d’ouvrir cette vidéo." }
                            file.outputStream().use { output ->
                                val buffer=ByteArray(65536); var bytes=0L
                                while(true) { val n=input.read(buffer); if(n<0) break; bytes+=n; check(bytes<=200L*1024*1024) { "Vidéo trop volumineuse : maximum 200 Mo." }; output.write(buffer,0,n) }
                            }
                        }
                    }
                    prepare(file,"import")
                } catch(e: Exception) { file.delete(); error=e.message; busy=false }
            }
        }
    }
    DisposableEffect(permitted, lifecycle) {
        var disposed = false
        val future = if(permitted) ProcessCameraProvider.getInstance(context) else null
        future?.addListener({
            if(!disposed) {
                try {
                    val provider=future.get()
                    val preview=Preview.Builder().build().also { it.surfaceProvider=previewView.surfaceProvider }
                    val recorder=Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD, FallbackStrategy.lowerQualityOrHigherThan(Quality.FHD))).build()
                    val video=VideoCapture.withOutput(recorder)
                    provider.unbindAll()
                    provider.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,video)
                    capture=video
                } catch(e: Exception) { error="Caméra indisponible. Autorisez son accès ou importez une vidéo de test." }
            }
        },ContextCompat.getMainExecutor(context))
        onDispose { disposed=true; recording?.close(); if(future?.isDone==true) runCatching { future.get().unbindAll() } }
    }
    LaunchedEffect(recording) { if(recording != null) { delay(15000); recording?.stop() } }
    if(face == null) { LaunchedEffect(Unit) { onDone() }; return }
    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(16.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("${faces[face]} · ${saved.size + 1}/4", style=MaterialTheme.typography.headlineMedium)
        Text("Cadrez roues et rétroviseurs. Restez immobile 2 à 3 secondes. Droite et gauche : côté conducteur.")
        LinearProgressIndicator(progress={ saved.size/4f },modifier=Modifier.fillMaxWidth())
        val item=pending
        if(item != null) {
            val bitmap=remember(item) { BitmapFactory.decodeFile(item.frame.absolutePath) }
            if(bitmap!=null) Image(bitmap.asImageBitmap(), "Image retenue : ${faces[face]}", Modifier.fillMaxWidth().heightIn(max=320.dp))
            Text("Vérifiez que cette face est nette et entièrement visible.")
            if(item.source=="import") Text("Vidéo importée : test, heure de prise de vue non attestée.")
            Button(onClick={
                busy=true
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { store.saveMedia(rentalId,kind,face,item.video,item.frame,item.source) }
                        pending=null; saved=store.media(rentalId,kind)
                    } catch(e: Exception) { error=e.message ?: "Enregistrement impossible." }
                    finally { busy=false }
                }
            }, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Text("Conserver cette face") }
            OutlinedButton(onClick={ item.video.delete(); item.frame.delete(); File(item.frame.absolutePath+".json").delete(); pending=null }, enabled=!busy, modifier=Modifier.fillMaxWidth()) { Text("Refaire cette face") }
        } else {
            if(permitted) AndroidView(factory={previewView},modifier=Modifier.fillMaxWidth().height(240.dp).clipToBounds())
            else OutlinedButton(onClick={permission.launch(Manifest.permission.CAMERA)},modifier=Modifier.fillMaxWidth()) { Text("Autoriser la caméra") }
            Button(onClick={
                if(recording!=null) { recording?.stop(); return@Button }
                if(context.filesDir.usableSpace < 300L*1024*1024) { error="Espace insuffisant. Libérez au moins 300 Mo."; return@Button }
                val file=store.newVideo()
                try {
                    recording=capture?.output?.prepareRecording(context,FileOutputOptions.Builder(file).build())?.start(ContextCompat.getMainExecutor(context)) { event ->
                        if(event is VideoRecordEvent.Finalize) {
                            recording=null
                            if(event.hasError()) { file.delete(); error="Capture interrompue. Reprenez cette face." }
                            else prepare(file,"camera")
                        }
                    }
                } catch(e: Exception) { file.delete(); error="Impossible de filmer. Vérifiez la permission caméra." }
            },enabled=permitted && capture!=null && !busy,modifier=Modifier.fillMaxWidth()) { Text(if(recording!=null) "Arrêter la vidéo" else "Filmer cette face") }
            if(recording!=null) Text("Enregistrement en cours · arrêt automatique après 15 secondes.")
            if(BuildConfig.DEBUG) OutlinedButton(onClick={picker.launch("video/*")},enabled=!busy && recording==null,modifier=Modifier.fillMaxWidth()) { Text("Importer une vidéo de test") }
        }
        if(busy) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Préparation de l’image…") }
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
        Text("Quatre faces ne montrent pas tout le dessous ni tous les angles.",style=MaterialTheme.typography.bodySmall)
    }
}
