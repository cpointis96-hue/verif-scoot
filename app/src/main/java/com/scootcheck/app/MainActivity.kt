package com.scootcheck.app

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.DateFormat
import java.util.Date

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT),
            navigationBarStyle=SystemBarStyle.light(android.graphics.Color.TRANSPARENT,android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme=lightColorScheme(primary=Color(0xFF175BA8),onPrimary=Color.White,surface=Color(0xFFFAFAFC),onSurface=Color(0xFF191C20))) { ScootApp() }
        }
    }
}
fun dateText(time: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT,DateFormat.SHORT).format(Date(time))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScootApp() {
    val context=LocalContext.current
    val store=remember { InspectionStore(context) }
    DisposableEffect(Unit) { onDispose { store.close() } }
    var screen by rememberSaveable { mutableStateOf("fleet") }
    var scooterId by rememberSaveable { mutableLongStateOf(-1) }
    var rentalId by rememberSaveable { mutableLongStateOf(-1) }
    var kind by rememberSaveable { mutableStateOf("departure") }
    var face by rememberSaveable { mutableIntStateOf(0) }
    var revision by remember { mutableIntStateOf(0) }
    var adding by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var search by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exporting by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val scooters=remember(revision) { store.scooters() }
    val scooter=scooters.find { it.id==scooterId }
    val rentals=remember(scooterId,revision) { if(scooterId>0) store.rentals(scooterId) else emptyList() }
    val rental=rentals.find { it.id==rentalId }
    fun back() { error=null; screen=when(screen) { "capture","review" -> "rental"; "rental" -> "scooter"; else -> "fleet" }; revision++ }
    BackHandler(screen!="fleet") { back() }
    val title=when(screen) { "fleet" -> "Scooters"; "capture" -> if(kind=="departure") "Inspection départ" else "Inspection retour"; "review" -> "Vérifier ${faces[face].lowercase()}"; "rental" -> "Inspection · ${scooter?.label.orEmpty()}"; else -> scooter?.label.orEmpty() }
    Scaffold(topBar={ TopAppBar(title={Text(title,maxLines=1,overflow=TextOverflow.Ellipsis)},navigationIcon={if(screen!="fleet") TextButton(onClick={back()}) { Text("Retour") }}) }) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when(screen) {
                "capture" -> CaptureScreen(store,rentalId,kind) { revision++; screen="rental" }
                "review" -> ReviewScreen(store,rentalId,face,rental?.closed==true) { revision++; screen="rental" }
                else -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
                    if(screen=="fleet") {
                        if(scooters.isEmpty()) Text("Ajoutez le premier scooter pour préparer son inspection.")
                        else OutlinedTextField(search,{search=it},label={Text("Rechercher un scooter")},singleLine=true,modifier=Modifier.fillMaxWidth())
                        Button(onClick={adding=true; label=""; error=null},modifier=Modifier.fillMaxWidth()) { Text("Ajouter un scooter") }
                        scooters.filter { it.label.contains(search,true) }.forEach { s ->
                            ListItem(headlineContent={Text(s.label)},modifier=Modifier.clickable { scooterId=s.id; screen="scooter"; revision++ })
                            HorizontalDivider()
                        }
                        if(scooters.isNotEmpty() && scooters.none { it.label.contains(search,true) }) Text("Aucun scooter correspondant.")
                    } else if(screen=="scooter") {
                        val open=rentals.firstOrNull { !it.closed }
                        Button(onClick={
                            try { rentalId=open?.id ?: store.openRental(scooterId); revision++; screen="rental" }
                            catch(e:Exception) { error=e.message }
                        },modifier=Modifier.fillMaxWidth()) { Text(if(open==null) "Nouvelle location" else "Reprendre la location") }
                        if(rentals.any { it.closed }) Text("Historique",style=MaterialTheme.typography.titleMedium)
                        rentals.filter { it.closed }.forEach { r ->
                            OutlinedButton(onClick={rentalId=r.id; screen="rental"; revision++},modifier=Modifier.fillMaxWidth()) { Text(dateText(r.created)) }
                        }
                    } else if(screen=="rental" && rental!=null) {
                        val departure=remember(revision,rentalId) { store.media(rentalId,"departure") }
                        val returned=remember(revision,rentalId) { store.media(rentalId,"return") }
                        val reviews=remember(revision,rentalId) { store.reviews(rentalId) }
                        Text(dateText(rental.created),style=MaterialTheme.typography.bodyMedium)
                        Text(if(rental.closed) "Location clôturée" else "Sauvegardé sur ce téléphone")
                        when {
                            departure.size<4 -> {
                                Text("Départ : ${departure.size}/4 faces conservées.")
                                Button(onClick={kind="departure"; screen="capture"},modifier=Modifier.fillMaxWidth()) { Text("Inspection départ") }
                            }
                            returned.size<4 -> {
                                Text("Départ conservé. Au retour, reprenez les mêmes angles.")
                                Button(onClick={kind="return"; screen="capture"},modifier=Modifier.fillMaxWidth()) { Text("Inspection retour") }
                                if(returned.isNotEmpty()) Text("Retour : ${returned.size}/4 faces conservées.")
                            }
                            else -> {
                                Text("Vérifiez les quatre faces. Les suggestions ne certifient pas un dommage.")
                                faces.forEachIndexed { index,name ->
                                    val review=reviews.find { it.face==index }
                                    OutlinedButton(onClick={face=index; screen="review"},modifier=Modifier.fillMaxWidth()) {
                                        Text("$name · ${when(review?.decision) { "confirmed" -> "changement confirmé"; "ignored" -> "suggestion ignorée"; "checked" -> "vérifié manuellement"; else -> "à vérifier" }}")
                                    }
                                }
                                if(!rental.closed) Button(onClick={try {store.closeRental(rentalId); revision++} catch(e:Exception) {error=e.message}},enabled=reviews.size==4,modifier=Modifier.fillMaxWidth()) { Text("Clôturer la location") }
                            }
                        }
                        if(departure.isNotEmpty()) OutlinedButton(onClick={
                            exporting=true; error=null
                            scope.launch {
                                try {
                                    val file=withContext(Dispatchers.IO) { EvidenceExport.export(context,store,rental,scooter?.label.orEmpty()) }
                                    val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file)
                                    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type="application/zip"; putExtra(Intent.EXTRA_STREAM,uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) },"Exporter le rapport"))
                                } catch(e:Exception) { error=e.message ?: "Export impossible." }
                                finally { exporting=false }
                            }
                        },enabled=!exporting,colors=if(rental.closed) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),modifier=Modifier.fillMaxWidth()) { Text(if(exporting) "Préparation du rapport…" else "Exporter le rapport et les médias") }
                        Text("Comparaison expérimentale, sans validation terrain. Quatre faces, couverture partielle. Aucun envoi cloud.",style=MaterialTheme.typography.bodySmall)
                    }
                    error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
    if(adding) AlertDialog(onDismissRequest={adding=false},title={Text("Ajouter un scooter")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(label,{label=it.take(60)},label={Text("Identifiant du scooter")},singleLine=true)
        error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }},confirmButton={TextButton(onClick={
        try { scooterId=store.createScooter(label); revision++; adding=false; screen="scooter"; error=null }
        catch(e:Exception) { error="Identifiant vide ou déjà utilisé. Choisissez un identifiant unique." }
    }) { Text("Ajouter") }},dismissButton={TextButton(onClick={adding=false}) {Text("Annuler")}})
}

@Composable
fun ReviewScreen(store: InspectionStore, rentalId: Long, face: Int, closed: Boolean, onDone: () -> Unit) {
    val before=remember { store.media(rentalId,"departure").first { it.face==face } }
    val after=remember { store.media(rentalId,"return").first { it.face==face } }
    val savedReview=remember { store.reviews(rentalId).find { it.face==face } }
    var a by remember { mutableStateOf<Bitmap?>(null) }
    var b by remember { mutableStateOf<Bitmap?>(null) }
    var result by remember { mutableStateOf<Comparison?>(null) }
    var note by rememberSaveable { mutableStateOf(store.reviews(rentalId).find { it.face==face }?.note.orEmpty()) }
    var error by remember { mutableStateOf<String?>(null) }
    var zoom by remember { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(rentalId,face) {
        withContext(Dispatchers.Default) {
            val aa=BitmapFactory.decodeFile(File(store.mediaDir,before.frame).absolutePath)
            val bb=BitmapFactory.decodeFile(File(store.mediaDir,after.frame).absolutePath)
            a=aa; b=bb
            result=if(closed && savedReview!=null) Comparison.fromJson(savedReview.analysis)
                else if(aa!=null && bb!=null) VisualComparator.compare(aa,bb) else Comparison("uncomparable","Image introuvable. Vérifiez les médias exportés.")
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        val comparison=result
        if(comparison==null) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Comparaison en cours…") }
        else {
            Text(when(comparison.status) { "candidate" -> "Changement visuel possible"; "quiet" -> "Revue visuelle requise"; else -> "Non comparable automatiquement" },style=MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Avant",style=MaterialTheme.typography.titleMedium)
                a?.let { bitmap -> FrameView(bitmap,comparison?.boxes.orEmpty()) { zoom=bitmap } }
            }
            Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("Après",style=MaterialTheme.typography.titleMedium)
                b?.let { bitmap -> FrameView(bitmap,emptyList()) { zoom=bitmap } }
            }
        }
        comparison?.let { Text(it.reason) }
        if(before.source=="import" || after.source=="import") Text("Médias importés pour test : heure de capture non attestée.",style=MaterialTheme.typography.bodySmall)
        Text("Touchez une image pour zoomer. Les cadres indiquent des différences dans la vue du départ, pas des dommages certifiés.",style=MaterialTheme.typography.bodySmall)
        if(!closed) {
            OutlinedTextField(note,{note=it.take(2000)},label={Text("Note de vérification")},modifier=Modifier.fillMaxWidth())
            fun decide(decision:String) { try { store.review(rentalId,face,decision,note,checkNotNull(result).json()); onDone() } catch(e:Exception) {error=e.message} }
            if(comparison?.status=="candidate") {
                Button(onClick={decide("confirmed")},modifier=Modifier.fillMaxWidth()) { Text("Confirmer le changement") }
                OutlinedButton(onClick={decide("ignored")},modifier=Modifier.fillMaxWidth()) {Text("Ignorer la suggestion")}
            } else {
                Button(onClick={decide("checked")},enabled=comparison!=null && a!=null && b!=null,modifier=Modifier.fillMaxWidth()) {Text("J’ai vérifié cette face")}
                OutlinedButton(onClick={decide("confirmed")},enabled=comparison!=null && a!=null && b!=null,modifier=Modifier.fillMaxWidth()) {Text("Signaler un changement visible")}
            }
        } else if(savedReview!=null) {
            Text(when(savedReview.decision) { "confirmed" -> "Changement confirmé par l’opérateur"; "ignored" -> "Suggestion ignorée par l’opérateur"; else -> "Face vérifiée manuellement" })
            if(savedReview.note.isNotBlank()) Text(savedReview.note)
        }
        error?.let {Text(it,color=MaterialTheme.colorScheme.error)}
        Text("Le masque central n’isole pas le scooter. Inspectez aussi les bords, les angles cachés et les petits détails.",style=MaterialTheme.typography.bodySmall)
    }
    zoom?.let { bitmap ->
        Dialog(onDismissRequest={zoom=null},properties=DialogProperties(usePlatformDefaultWidth=false)) {
            var scale by remember { mutableFloatStateOf(1f) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    TextButton(onClick={zoom=null}) {Text("Fermer le zoom")}
                    Box(Modifier.weight(1f).fillMaxWidth().pointerInput(Unit) { detectTransformGestures { _,pan,factor,_ -> scale=(scale*factor).coerceIn(1f,6f); offset=if(scale==1f) Offset.Zero else offset+pan } }) {
                        Image(bitmap.asImageBitmap(),"Image originale agrandie",Modifier.fillMaxSize().graphicsLayer {scaleX=scale; scaleY=scale; translationX=offset.x; translationY=offset.y})
                    }
                }
            }
        }
    }
}

@Composable
fun FrameView(bitmap: Bitmap, boxes: List<ChangeBox>, onZoom: () -> Unit) {
    Box(Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat()/bitmap.height).clickable(onClickLabel="Agrandir l’image",onClick=onZoom)) {
        Image(bitmap.asImageBitmap(),"Vue du scooter",Modifier.fillMaxSize())
        Canvas(Modifier.matchParentSize()) { boxes.forEach { b -> drawRect(Color(0xFFB34700),Offset(b.x*size.width,b.y*size.height),Size(b.width*size.width,b.height*size.height),style=Stroke(3.dp.toPx())) } }
    }
}
