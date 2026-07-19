package lk.kdu.ac.mc.newlifelog
import androidx.room.Update
import android.os.Bundle
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import android.content.Context
import android.util.Log
import androidx.compose.material.icons.filled.Edit
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState

import androidx.compose.material.icons.filled.Brightness4
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.*
import lk.kdu.ac.mc.newlifelog.ui.theme.NewlifelogTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import android.widget.Toast
import java.io.FileOutputStream


import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.File
import android.media.MediaPlayer
import com.google.firebase.firestore.FirebaseFirestore

import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.ui.graphics.Color
import android.speech.RecognizerIntent
import android.app.Activity
import androidx.compose.material.icons.filled.Mic
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Alignment

import androidx.compose.foundation.background

import androidx.biometric.BiometricPrompt
import androidx.biometric.BiometricManager

import androidx.fragment.app.FragmentActivity
import androidx.compose.material.icons.filled.Lock
import com.google.android.gms.location.LocationServices
import android.location.Geocoder

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.RepeatMode

import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Fill

import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke

import java.util.Locale

// --- 1. DATABASE KOTASA ---
@Entity(tableName = "logs_table")
data class LifeLog(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val imageUri: String? = null,
    val audioUri: String? = null,
    val mood: String = "😐 Neutral",
    val location: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Dao
interface LogDao {
    @Insert
    suspend fun insertLog(log: LifeLog)

    // Me aluth line eka ekathu karanna
    @Delete
    suspend fun deleteLog(log: LifeLog)
    @Update
    suspend fun updateLog(log: LifeLog)

    @Query("SELECT * FROM logs_table ORDER BY timestamp DESC")
    fun getAllLogs(): kotlinx.coroutines.flow.Flow<List<LifeLog>>
}

@Database(entities = [LifeLog::class], version = 5, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun logDao(): LogDao
}


// --- 2. VIEWMODEL ---
class LogViewModel(private val dao: LogDao) : ViewModel() {
    val logs = dao.getAllLogs().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val firestore = FirebaseFirestore.getInstance()

    fun saveLog(title: String, description: String, imageUri: String?, audioUri: String?, mood: String, location: String?) {
        viewModelScope.launch {
            val newLog = LifeLog(title = title, description = description, imageUri = imageUri, audioUri = audioUri, mood = mood, location = location)
            dao.insertLog(newLog)
            syncToFirebase(newLog)
        }
    }

    fun deleteLog(log: LifeLog) {
        viewModelScope.launch {
            dao.deleteLog(log)
            firestore.collection("MyLogs").document(log.timestamp.toString()).delete()
        }
    }

    fun updateLog(updatedLog: LifeLog) {
        viewModelScope.launch {
            try {
                dao.updateLog(updatedLog)


            } catch (e: Exception) {
                Log.e("LyraAI", "Error updating log: ${e.message}")
            }
        }
    }

    private fun syncToFirebase(log: LifeLog) {
        val cloudData = hashMapOf(
            "title" to log.title,
            "description" to log.description,
            "timestamp" to log.timestamp,
            "mood" to log.mood,
            "location" to log.location
        )
        firestore.collection("MyLogs")
            .document(log.timestamp.toString())
            .set(cloudData)
            .addOnSuccessListener { println("Firebase Sync Success!") }
            .addOnFailureListener { e -> println("Firebase Sync Failed: ${e.message}") }
    }

    fun getSmartMoodInsight(context: Context, recentLogs: List<LifeLog>): String {

        try {

            val floatMoods = FloatArray(7) { 1.0f }
            val takeCount = minOf(recentLogs.size, 7)
            for (i in 0 until takeCount) {
                val moodString = recentLogs[recentLogs.size - takeCount + i].mood
                floatMoods[7 - takeCount + i] = when {
                    moodString.contains("Stressed", ignoreCase = true) || moodString.contains("Sad", ignoreCase = true) -> 0f
                    moodString.contains("Happy", ignoreCase = true) -> 2f
                    else -> 1f
                }
            }


            val inputBuffer = Array(1) { Array(7) { FloatArray(1) } }
            for (i in 0 until 7) {
                inputBuffer[0][i][0] = floatMoods[i]
            }


            val outputBuffer = Array(1) { FloatArray(3) }


            val interpreter = org.tensorflow.lite.Interpreter(loadModelFile(context, "mood_model.tflite"))
            interpreter.run(inputBuffer, outputBuffer)
            interpreter.close()


            val result = outputBuffer[0]
            val maxIndex = result.indices.maxByOrNull { result[it] } ?: 1


            return when (maxIndex) {
                0 -> "Lyra notices you've been stressed lately. Try a 5-min breathing exercise. 🧘‍♀️"
                2 -> "You're having a great week! Keep this positive energy flowing! 🌟"
                else -> "Your mood seems balanced. Keep writing! ✨"
            }
        } catch (e: Exception) {
            e.printStackTrace()

            Log.e("LyraAI", "Error processing AI: ${e.message}")
            return "Error: ${e.message}"
        }
    }

    private fun loadModelFile(context: Context, fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}

// --- 3. MAIN ACTIVITY & UI ---
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val db = Room.databaseBuilder(applicationContext, AppDatabase::class.java, "life_logger_db")
            .fallbackToDestructiveMigration() // Aluthin ekathu karapu line eka
            .build()

        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return LogViewModel(db.logDao()) as T
            }
        }


        setContent {

            var isDarkTheme by remember { mutableStateOf(true) }


            val viewModel: LogViewModel = viewModel(factory = factory)
            val logs by viewModel.logs.collectAsState(initial = emptyList())
            val context = LocalContext.current

            NewlifelogTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {


                    Column(modifier = Modifier.fillMaxSize()) {


                        val insight =
                            remember(logs) { viewModel.getSmartMoodInsight(context, logs) }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Text(
                                text = insight,
                                modifier = Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }


                        AppNavigation(
                            viewModel = viewModel,
                            isDarkTheme = isDarkTheme,
                            onToggleTheme = { isDarkTheme = !isDarkTheme }
                        )
                    }
                }
            }
        }
    }
}

        @Composable
        fun AppNavigation(
            viewModel: LogViewModel,
            isDarkTheme: Boolean,
            onToggleTheme: () -> Unit
        ) {
            val context = LocalContext.current
            val sharedPreferences =
                context.getSharedPreferences("LifeLogPrefs", Context.MODE_PRIVATE)

            val isFirstTime = sharedPreferences.getBoolean("IS_FIRST_TIME", true)
            val isLoggedIn = sharedPreferences.getBoolean("IS_LOGGED_IN", false)

            var currentScreen by remember { mutableStateOf("splash") }
            // අලුතින් මේක දාන්න (Edit කරන්න ඕන Log එකේ ID එක මතක තියාගන්න)
            var editLogId by remember { mutableStateOf<Int?>(null) }

            when (currentScreen) {
                "splash" -> SplashScreen(
                    onTimeout = {

                        currentScreen = when {
                            isFirstTime -> "welcome_hub"
                            isLoggedIn -> "home"
                            else -> "login"
                        }
                    }
                )

                "welcome_hub" -> LyraWelcomeHub(
                    onProceed = {

                        sharedPreferences.edit().putBoolean("IS_FIRST_TIME", false).apply()
                        currentScreen = "register"
                    }
                )

                "login" -> LoginScreen(
                    onLoginSuccess = {
                        sharedPreferences.edit().putBoolean("IS_LOGGED_IN", true).apply()
                        currentScreen = "home"
                    },
                    onNavigateToRegister = { currentScreen = "register" }
                )

                "register" -> RegisterScreen(
                    onRegisterSuccess = {
                        sharedPreferences.edit().putBoolean("IS_LOGGED_IN", true).apply()
                        currentScreen = "home"
                    },
                    onNavigateToLogin = { currentScreen = "login" }
                )


                "home" -> HomeScreen(
                    viewModel = viewModel,
                    onNavigateToAdd = { currentScreen = "add" },
                    onLogout = {
                        sharedPreferences.edit().putBoolean("IS_LOGGED_IN", false).apply()
                        currentScreen = "login"
                    },
                    isDark = isDarkTheme,
                    onToggleTheme = onToggleTheme,
                    onEditLog = { logId ->
                        // මෙතනින් ID එක සේව් කරගෙන, edit ස්ක්‍රීන් එකට යන්න කමාන්ඩ් එක දෙනවා
                        editLogId = logId
                        currentScreen = "edit"
                    }
                )

                "add" -> AddLogScreen(
                    viewModel = viewModel,
                    onNavigateBack = { currentScreen = "home" }
                )


                "edit" -> {
                    if (editLogId != null) {
                        EditLogScreen(
                            logId = editLogId!!,
                            viewModel = viewModel,
                            onNavigateBack = { currentScreen = "home" }
                        )
                    }
                }
            }
        }


        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun HomeScreen(
            viewModel: LogViewModel,
            onNavigateToAdd: () -> Unit,
            onLogout: () -> Unit,
            isDark: Boolean,
            onToggleTheme: () -> Unit,
            onEditLog: (Int) -> Unit
        ) {
            val logsList by viewModel.logs.collectAsState()
            var searchQuery by remember { mutableStateOf("") }
            val context = LocalContext.current
            var mediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
            var currentlyPlayingId by remember { mutableStateOf<Int?>(null) }


            val listState = rememberLazyListState()
            val scrollOffset =
                (listState.firstVisibleItemIndex * 300f) + listState.firstVisibleItemScrollOffset

            val filteredLogs = logsList.filter {
                it.title.contains(searchQuery, ignoreCase = true) || it.description.contains(
                    searchQuery,
                    ignoreCase = true
                )
            }

            Box(modifier = Modifier.fillMaxSize()) {

                StarryBackground(scrollOffset = scrollOffset)

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Lyra - Smart Journal",
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontFamily = FontFamily.Cursive,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = Color(0xFFF9A825)
                                )
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(
                                    0xFF0A2540
                                ).copy(alpha = 0.85f)
                            ),
                            actions = {

                                IconButton(onClick = onToggleTheme) {
                                    Icon(
                                        imageVector = Icons.Filled.Brightness4,
                                        contentDescription = "Toggle Theme",
                                        tint = Color(0xFFF9A825)
                                    )
                                }

                                IconButton(onClick = onLogout) {
                                    Icon(
                                        imageVector = Icons.Filled.ExitToApp,
                                        contentDescription = "Logout",
                                        tint = Color(0xFFF9A825)
                                    )
                                }
                            }
                        )
                    },
                    floatingActionButton = {
                        FloatingActionButton(
                            onClick = onNavigateToAdd,
                            containerColor = Color(0xFFF9A825),
                            contentColor = Color(0xFF0A2540)
                        ) {
                            Icon(Icons.Filled.Add, "Add")
                        }
                    }
                ) { innerPadding ->
                    Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {

                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            label = { Text("Search memories...", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9A825),
                                unfocusedBorderColor = Color.LightGray,
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )

                        if (filteredLogs.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "No memories found in the stars yet.",
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                            }
                        } else {
                            LazyColumn(
                                state = listState, // List State එක සෙට් කරනවා
                                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                items(filteredLogs.size) { index ->
                                    val log = filteredLogs[index]
                                    // Glassmorphism Card එක (වීදුරු වගේ)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = Color.White.copy(
                                                alpha = 0.15f
                                            )
                                        )
                                    ) {
                                        Column(modifier = Modifier.padding(16.dp)) {

                                            if (log.imageUri != null) {
                                                AsyncImage(
                                                    model = log.imageUri,
                                                    contentDescription = "Log Image",
                                                    contentScale = ContentScale.Crop,
                                                    modifier = Modifier.fillMaxWidth()
                                                        .height(200.dp).padding(bottom = 8.dp)
                                                )
                                            }

                                            Text(
                                                text = log.title,
                                                style = MaterialTheme.typography.titleLarge,
                                                color = Color(0xFFF9A825),
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = log.description,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = Color.White,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )

                                            if (!log.location.isNullOrEmpty()) {
                                                Text(
                                                    text = "${log.location}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = Color.LightGray,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }

                                            Text(
                                                text = "Mood: ${log.mood}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = Color(0xFF81D4FA),
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) // Light blue mood text

                                            if (log.audioUri != null) {
                                                val isPlaying = currentlyPlayingId == log.id
                                                Button(
                                                    onClick = {
                                                        if (isPlaying) {
                                                            mediaPlayer?.stop(); mediaPlayer?.release(); mediaPlayer =
                                                                null; currentlyPlayingId = null
                                                        } else {
                                                            try {
                                                                mediaPlayer?.release()
                                                                mediaPlayer = MediaPlayer().apply {
                                                                    setDataSource(log.audioUri); prepare(); start()
                                                                    setOnCompletionListener {
                                                                        currentlyPlayingId = null
                                                                    }
                                                                }
                                                                currentlyPlayingId = log.id
                                                            } catch (e: Exception) {
                                                                e.printStackTrace()
                                                            }
                                                        }
                                                    },
                                                    modifier = Modifier.padding(top = 8.dp),
                                                    colors = ButtonDefaults.buttonColors(
                                                        containerColor = if (isPlaying) Color(
                                                            0xFFFF6B6B
                                                        ) else Color(0xFFF9A825)
                                                    )
                                                ) {
                                                    Text(
                                                        if (isPlaying) "Stop Audio" else "Play Audio",
                                                        color = Color(0xFF0A2540),
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }

                                            val dateString = SimpleDateFormat(
                                                "dd MMM yyyy, hh:mm a",
                                                Locale.getDefault()
                                            ).format(Date(log.timestamp))
                                            Text(
                                                text = dateString,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.LightGray,
                                                modifier = Modifier.padding(
                                                    top = 8.dp,
                                                    bottom = 8.dp
                                                )
                                            )

                                            Divider(color = Color.White.copy(alpha = 0.2f))

                                            Row(
                                                modifier = Modifier.fillMaxWidth()
                                                    .padding(top = 8.dp),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                IconButton(onClick = {
                                                    val sendIntent: Intent = Intent().apply {
                                                        action = Intent.ACTION_SEND
                                                        putExtra(
                                                            Intent.EXTRA_TEXT,
                                                            "Look at my Lyra memory: ${log.title}\n${log.description}"
                                                        )
                                                        type = "text/plain"
                                                    }
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            sendIntent,
                                                            null
                                                        )
                                                    )
                                                }) {
                                                    Icon(
                                                        Icons.Filled.Share,
                                                        contentDescription = "Share",
                                                        tint = Color(0xFFF9A825)
                                                    )
                                                }

                                                IconButton(onClick = { viewModel.deleteLog(log) }) {
                                                    Icon(
                                                        Icons.Filled.Delete,
                                                        contentDescription = "Delete",
                                                        tint = Color(0xFFFF6B6B)
                                                    )
                                                }
                                                IconButton(onClick = {
                                                    // දැන් අපි අර උඩින් හදාගත්තු function එකට Log ID එක යවනවා
                                                    onEditLog(log.id)
                                                }) {
                                                    Icon(
                                                        imageVector = Icons.Filled.Edit, // Default වෙනුවට Filled පාවිච්චි කරන්න
                                                        contentDescription = "Edit Log",
                                                        tint = Color(0xFF4CAF50) // කොළ පාටක් වගේ දුන්නොත් ලස්සනට පෙනේවි (හෝ ඔයා කැමති පාටක්)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }


        @OptIn(ExperimentalMaterial3Api::class)
        @Composable
        fun AddLogScreen(viewModel: LogViewModel, onNavigateBack: () -> Unit) {
            val context = LocalContext.current
            var title by remember { mutableStateOf("") }
            var location by remember { mutableStateOf("") }
            var description by remember { mutableStateOf("") }
            var imageUri by remember { mutableStateOf<String?>(null) }
            var audioUri by remember { mutableStateOf<String?>(null) }
            var isRecording by remember { mutableStateOf(false) }
            var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
            var locationText by remember { mutableStateOf("No location selected") }
            val s3Uploader = remember { S3Uploader(context) }
            val coroutineScope = rememberCoroutineScope()

            val locationPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
                    if (isGranted) {
                        val fusedLocationClient =
                            LocationServices.getFusedLocationProviderClient(context)
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                if (location != null) {
                                    val geocoder = Geocoder(context, Locale.getDefault())
                                    try {
                                        val addresses = geocoder.getFromLocation(
                                            location.latitude,
                                            location.longitude,
                                            1
                                        )
                                        locationText = addresses?.get(0)?.getAddressLine(0)
                                            ?: "Lat: ${location.latitude}, Lon: ${location.longitude}"
                                    } catch (e: Exception) {
                                        locationText =
                                            "Lat: ${location.latitude}, Lon: ${location.longitude}"
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            locationText = "Permission denied"
                        }
                    }
                }

            val speechToTextLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                    if (result.resultCode == Activity.RESULT_OK) {
                        val data = result.data
                        val matches = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                        if (!matches.isNullOrEmpty()) {
                            val spokenText = matches[0]
                            description += if (description.isEmpty()) spokenText else " $spokenText"
                        }
                    }
                }

            val imageLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
                    if (uri != null) {
                        context.contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                        )
                        imageUri = uri.toString()
                    }
                }

            val audioPermissionLauncher =
                rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

            fun startRecording() {
                val file = File(context.cacheDir, "audio_${System.currentTimeMillis()}.3gp")
                val recorder =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(context) else @Suppress(
                        "DEPRECATION"
                    ) MediaRecorder()
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                recorder.setOutputFile(file.absolutePath)
                try {
                    recorder.prepare(); recorder.start(); mediaRecorder = recorder; isRecording =
                        true; audioUri = file.absolutePath
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            fun stopRecording() {
                try {
                    mediaRecorder?.stop(); mediaRecorder?.release(); mediaRecorder =
                        null; isRecording = false
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {

                StarryBackground(scrollOffset = 0f)

                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    "Add Memory to Lyra",
                                    color = Color(0xFFF9A825),
                                    fontWeight = FontWeight.Bold
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = onNavigateBack) {
                                    Icon(
                                        Icons.AutoMirrored.Filled.ArrowBack,
                                        "Back",
                                        tint = Color(0xFFF9A825)
                                    )
                                }
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = Color(
                                    0xFF0A2540
                                ).copy(alpha = 0.85f)
                            )
                        )
                    }
                ) { innerPadding ->
                    Column(
                        modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {

                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Title", color = Color.LightGray) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(
                                    0xFFF9A825
                                ),
                                unfocusedBorderColor = Color.LightGray,
                                focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                            )
                        )

                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = description,
                                onValueChange = { description = it },
                                label = { Text("Description", color = Color.LightGray) },
                                modifier = Modifier.weight(1f).height(150.dp),
                                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Color(
                                        0xFFF9A825
                                    ),
                                    unfocusedBorderColor = Color.LightGray,
                                    focusedContainerColor = Color.White.copy(alpha = 0.1f),
                                    unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                                )
                            )

                            IconButton(
                                onClick = {
                                    val intent =
                                        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                            putExtra(
                                                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                            )
                                        }
                                    speechToTextLauncher.launch(intent)
                                },
                                modifier = Modifier.padding(start = 8.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Mic,
                                    contentDescription = "Voice Type",
                                    tint = Color(0xFFF9A825)
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = { imageLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    if (imageUri == null) "Photo" else "Change Photo",
                                    color = Color(0xFFF9A825)
                                )
                            }

                            Button(
                                onClick = {
                                    if (ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                    ) {
                                        if (isRecording) stopRecording() else startRecording()
                                    } else {
                                        audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isRecording) Color(
                                        0xFFFF6B6B
                                    ) else Color.White.copy(alpha = 0.2f)
                                )
                            ) {
                                Text(
                                    if (isRecording) "Stop Rec" else if (audioUri != null) "Re-Record" else "Record Audio",
                                    color = if (isRecording) Color.White else Color(0xFFF9A825)
                                )
                            }
                        }

                        if (imageUri != null) {
                            AsyncImage(
                                model = imageUri,
                                contentDescription = "Selected Image",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxWidth().height(150.dp)
                            )
                        }

                        Button(
                            onClick = { locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White.copy(
                                    alpha = 0.2f
                                )
                            )
                        ) {
                            Text(
                                if (locationText == "No location selected") "Add Current Location" else "Location: $locationText",
                                color = Color.White
                            )
                        }

                        Button(
                            onClick = {
                                if (title.isNotBlank() && description.isNotBlank()) {
                                    if (isRecording) stopRecording()

                                    coroutineScope.launch {
                                        Toast.makeText(
                                            context,
                                            "Saving Memory to Stars... ⏳",
                                            Toast.LENGTH_LONG
                                        ).show()
                                        var finalImageUri = imageUri
                                        var finalAudioUri = audioUri

                                        if (imageUri != null) {
                                            try {
                                                val uri = Uri.parse(imageUri)
                                                val tempFile = File(
                                                    context.cacheDir,
                                                    "img_${System.currentTimeMillis()}.jpg"
                                                )
                                                val inputStream =
                                                    context.contentResolver.openInputStream(uri)
                                                val outputStream = FileOutputStream(tempFile)
                                                inputStream?.copyTo(outputStream)
                                                inputStream?.close(); outputStream.close()

                                                if (s3Uploader.uploadMedia(
                                                        tempFile,
                                                        tempFile.name
                                                    )
                                                ) {
                                                    finalImageUri =
                                                        "https://venuri-lifelog-media.s3.ap-southeast-1.amazonaws.com/${tempFile.name}"
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        if (audioUri != null) {
                                            try {
                                                val audioFile = File(audioUri!!)
                                                val audioFileName =
                                                    "audio_${System.currentTimeMillis()}.3gp"
                                                if (s3Uploader.uploadMedia(
                                                        audioFile,
                                                        audioFileName
                                                    )
                                                ) {
                                                    finalAudioUri =
                                                        "https://venuri-lifelog-media.s3.ap-southeast-1.amazonaws.com/$audioFileName"
                                                }
                                            } catch (e: Exception) {
                                                e.printStackTrace()
                                            }
                                        }

                                        val calculatedMood = analyzeSentiment(context, description)
                                        viewModel.saveLog(
                                            title = title,
                                            description = description,
                                            imageUri = finalImageUri,
                                            audioUri = finalAudioUri,
                                            mood = calculatedMood,
                                            location = if (locationText == "No location selected") null else locationText
                                        )

                                        Toast.makeText(
                                            context,
                                            "Memory Saved Successfully! 🌟",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        onNavigateBack()
                                    }
                                } else {
                                    Toast.makeText(
                                        context,
                                        "Please enter title and description",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(55.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                        ) {
                            Text(
                                "Save to Lyra",
                                color = Color(0xFF0A2540),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditLogScreen(
    logId: Int,
    viewModel: LogViewModel,
    onNavigateBack: () -> Unit
) {

    val logs by viewModel.logs.collectAsState()
    val logToEdit = logs.find { it.id == logId }


    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var imageUri by remember { mutableStateOf<String?>(null) }


    LaunchedEffect(logToEdit) {
        if (logToEdit != null) {
            title = logToEdit!!.title
            description = logToEdit!!.description
            location = logToEdit!!.location ?: "" // පරණ Location එක පිරෙනවා
            imageUri = logToEdit!!.imageUri // පරණ ෆොටෝ එකේ ලින්ක් එක ගන්නවා
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        StarryBackground(scrollOffset = 0f)

        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {
                        Text("Edit Memory", color = Color(0xFFF9A825), fontWeight = FontWeight.Bold)
                    },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color(0xFFF9A825))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0A2540).copy(alpha = 0.85f))
                )
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (logToEdit == null) {
                    Text("Loading memory from the stars...", color = Color.White)
                } else {

                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Title", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF9A825),
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )


                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth().height(150.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF9A825),
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )


                    OutlinedTextField(
                        value = location,
                        onValueChange = { location = it },
                        label = { Text("Location (e.g., KDU, Colombo)", color = Color.LightGray) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFF9A825),
                            unfocusedBorderColor = Color.LightGray,
                            focusedContainerColor = Color.White.copy(alpha = 0.1f),
                            unfocusedContainerColor = Color.White.copy(alpha = 0.1f)
                        )
                    )


                    Button(
                        onClick = {
                            if (title.isNotBlank() && description.isNotBlank()) {


                                val updatedLog = logToEdit.copy(
                                    title = title,
                                    description = description,
                                    location = if (location.isBlank()) null else location,
                                    imageUri = imageUri ?: logToEdit.imageUri
                                )

                                viewModel.updateLog(updatedLog)
                                onNavigateBack()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(55.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                    ) {
                        Text(
                            "Save Changes",
                            color = Color(0xFF0A2540),
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}
        fun analyzeSentiment(context: Context, text: String): String {
            if (text.isBlank()) return "😐 Neutral"
            val lowerText = text.lowercase(Locale.getDefault())

            val negativeWords = listOf(
                "dukai",
                "awul",
                "epa",
                "kenti",
                "mala",
                "sad",
                "bad",
                "terrible",
                "angry",
                "tired",
                "stressed",
                "chater",
                "epawela",
                "kaha"
            )
            val positiveWords = listOf(
                "sathutai",
                "niyamai",
                "maru",
                "patta",
                "ela",
                "happy",
                "great",
                "awesome",
                "good",
                "love",
                "beautiful",
                "supiri",
                "fatta"
            )

            var finalScore = 0


            positiveWords.forEach { if (lowerText.contains(it)) finalScore += 2 }
            negativeWords.forEach { if (lowerText.contains(it)) finalScore -= 2 }



            return when {
                finalScore > 0 -> "Happy"
                finalScore < 0 -> "Stressed / Sad"
                else -> "Neutral"
            }
        }

        @Composable
        fun LyraWelcomeHub(onProceed: () -> Unit) {
            var textToShow by remember { mutableStateOf("") }
            val fullText =
                "Hello there! I'm Lyra. I'm here to help you capture your memories, manage your moods, and keep everything safe. Shall we start your journey?"

            LaunchedEffect(Unit) {
                fullText.forEach { char ->
                    textToShow += char
                    delay(50)
                }
            }

            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A2540)),
                contentAlignment = Alignment.Center
            ) {
                StarryBackground()

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {

                    LyraBot()

                    Spacer(modifier = Modifier.height(32.dp))

                    Text(
                        text = textToShow,
                        style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))

                    if (textToShow.length == fullText.length) {
                        Button(
                            onClick = onProceed,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825)),
                            modifier = Modifier.height(55.dp).fillMaxWidth(0.6f)
                        ) {
                            Text(
                                "Let's Begin",
                                color = Color(0xFF0A2540),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        @Composable
        fun LyraBot() {
            val infiniteTransition = rememberInfiniteTransition(label = "robot")
            val yOffset by infiniteTransition.animateFloat(
                initialValue = -20f,
                targetValue = 20f,
                animationSpec = infiniteRepeatable(
                    animation = tween(
                        1000,
                        easing = FastOutSlowInEasing
                    ), repeatMode = RepeatMode.Reverse
                ),
                label = "yOffset"
            )

            Canvas(modifier = Modifier.size(150.dp).offset(y = yOffset.dp)) {
                val center = Offset(size.width / 2, size.height / 2)


                drawRoundRect(
                    color = Color(0xFFF9A825),
                    size = androidx.compose.ui.geometry.Size(120.dp.toPx(), 100.dp.toPx()),
                    topLeft = Offset(size.width / 2 - 60.dp.toPx(), size.height / 2 - 50.dp.toPx()),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(
                        20.dp.toPx(),
                        20.dp.toPx()
                    )
                )


                drawCircle(
                    color = Color(0xFF0A2540),
                    radius = 10f,
                    center = Offset(center.x - 30f, center.y - 10f)
                )
                drawCircle(
                    color = Color(0xFF0A2540),
                    radius = 10f,
                    center = Offset(center.x + 30f, center.y - 10f)
                )


                drawArc(
                    color = Color(0xFF0A2540),
                    startAngle = 20f,
                    sweepAngle = 140f,
                    useCenter = false,
                    topLeft = Offset(center.x - 30f, center.y + 10f),
                    size = androidx.compose.ui.geometry.Size(60f, 30f),
                    style = Stroke(width = 5f)
                )
            }
        }
// --- AUTHENTICATION SCREENS (LOGIN & REGISTER) ---

        @Composable
        fun WelcomeScreen(onGetStarted: () -> Unit, onSkipToLogin: () -> Unit) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A2540)), // Professional Blue
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {

                    Text(
                        text = "Lyra",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Cursive,
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFFF9A825) // Dark Yellow
                    )
                    Spacer(modifier = Modifier.height(16.dp))


                    Text(
                        text = "Capture your daily memories, track your moods with AI, and keep your journal completely secure.",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.8f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(48.dp))


                    Button(
                        onClick = onGetStarted,
                        modifier = Modifier.fillMaxWidth().height(55.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Text(
                            "Get Started (Register)",
                            color = Color(0xFF0A2540),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))


                    TextButton(onClick = onSkipToLogin) {
                        Text(
                            "Already have an account? Login",
                            color = Color(0xFFF9A825),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        @Composable
        fun LoginScreen(onLoginSuccess: () -> Unit, onNavigateToRegister: () -> Unit) {
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }
            var showBiometricButton by remember { mutableStateOf(false) }

            val context = LocalContext.current
            val sharedPreferences =
                context.getSharedPreferences("LifeLogPrefs", Context.MODE_PRIVATE)

            LaunchedEffect(Unit) {
                val biometricManager = BiometricManager.from(context)
                if (biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS) {
                    showBiometricButton = true
                }
            }

            Box(
                modifier = Modifier.fillMaxSize()
                    .background(Color(0xFF0A2540)), // Professional Blue
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f)) // වීදුරුවක් වගේ පෙනුමක්
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Welcome Back",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            "Login to Lyra",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address", color = Color.LightGray) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9A825),
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Password", color = Color.LightGray) },
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFF9A825),
                                unfocusedBorderColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                errorMessage,
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                val savedEmail = sharedPreferences.getString("SAVED_EMAIL", null)
                                val savedPassword =
                                    sharedPreferences.getString("SAVED_PASSWORD", null)

                                if (savedEmail == null) {
                                    errorMessage = "No account found. Please register first."
                                } else if (email == savedEmail && password == savedPassword) {
                                    errorMessage = ""
                                    onLoginSuccess()
                                } else {
                                    errorMessage = "Incorrect email or password!"
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                        ) {
                            Text(
                                "Secure Login",
                                color = Color(0xFF0A2540),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (showBiometricButton) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedButton(
                                onClick = {
                                    val savedEmail =
                                        sharedPreferences.getString("SAVED_EMAIL", null)
                                    if (savedEmail == null) {
                                        errorMessage = "Please register an account first."
                                    } else {
                                        showBiometricPrompt(
                                            context = context,
                                            onSuccess = {
                                                errorMessage = ""
                                                onLoginSuccess()
                                            },
                                            onError = { error ->
                                                errorMessage = error
                                            }
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth().height(50.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(
                                        0xFFF9A825
                                    )
                                )
                            ) {
                                Icon(
                                    Icons.Filled.Lock,
                                    contentDescription = "Fingerprint",
                                    tint = Color(0xFFF9A825)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    "Use Fingerprint",
                                    color = Color(0xFFF9A825),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onNavigateToRegister) {
                            Text("Don't have an account? Register here", color = Color(0xFFF9A825))
                        }
                    }
                }
            }
        }

        @Composable
        fun RegisterScreen(onRegisterSuccess: () -> Unit, onNavigateToLogin: () -> Unit) {
            var name by remember { mutableStateOf("") }
            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var errorMessage by remember { mutableStateOf("") }

            val context = LocalContext.current
            val sharedPreferences =
                context.getSharedPreferences("LifeLogPrefs", Context.MODE_PRIVATE)

            Box(
                modifier = Modifier.fillMaxSize().background(Color(0xFF0A2540)),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier.padding(24.dp).fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "Create Account",
                            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            "Start your secure life log today",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Full Name", color = Color.LightGray) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(
                                    0xFFF9A825
                                ), unfocusedBorderColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("Email Address", color = Color.LightGray) },
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(
                                    0xFFF9A825
                                ), unfocusedBorderColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            label = { Text("Create Password", color = Color.LightGray) },
                            visualTransformation = PasswordVisualTransformation(),
                            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(
                                    0xFFF9A825
                                ), unfocusedBorderColor = Color.LightGray
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                errorMessage,
                                color = Color(0xFFFF6B6B),
                                style = MaterialTheme.typography.labelMedium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Button(
                            onClick = {
                                if (name.isBlank() || email.isBlank() || password.isBlank()) {
                                    errorMessage = "Please fill in all details."
                                } else if (name.length < 3) {
                                    errorMessage = "Name must be at least 3 characters long."
                                } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email)
                                        .matches()
                                ) {
                                    errorMessage = "Please enter a valid email format."
                                } else if (password.length < 6) {
                                    errorMessage = "Password must be at least 6 characters long."
                                } else {
                                    sharedPreferences.edit()
                                        .putString("SAVED_EMAIL", email)
                                        .putString("SAVED_PASSWORD", password)
                                        .apply()

                                    errorMessage = ""
                                    onRegisterSuccess()
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF9A825))
                        ) {
                            Text(
                                "Register & Enter",
                                color = Color(0xFF0A2540),
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(onClick = onNavigateToLogin) {
                            Text("Already have an account? Login", color = Color(0xFFF9A825))
                        }
                    }
                }
            }
        }

        // --- BIOMETRIC AUTHENTICATION ---
        fun showBiometricPrompt(
            context: Context,
            onSuccess: () -> Unit,
            onError: (String) -> Unit
        ) {
            val fragmentActivity = context as? FragmentActivity
            if (fragmentActivity == null) {
                onError("Biometric authentication is not supported on this device.")
                return
            }

            val executor = ContextCompat.getMainExecutor(context)
            val biometricPrompt = BiometricPrompt(
                fragmentActivity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        onError(errString.toString())
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess() // ඇඟිලි සලකුණ හරි නම් ඇතුළට යනවා!
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("Fingerprint not recognized. Try again.")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Biometric Login")
                .setSubtitle("Log in using your fingerprint or face")
                .setNegativeButtonText("Use Password Instead")
                .build()

            biometricPrompt.authenticate(promptInfo)
        }


        @Composable
        fun SplashScreen(onTimeout: () -> Unit) {
            val scale = remember { Animatable(0f) }
            val alpha = remember { Animatable(0f) }

            LaunchedEffect(key1 = true) {

                scale.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing)
                )

                alpha.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 800)
                )

                delay(2500L)
                onTimeout()
            }


            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A2540)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    Text(
                        text = "Lyra",
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontFamily = FontFamily.Cursive, // ලස්සන අත් අකුරු (Handwriting) ස්ටයිල් එකක්
                            fontWeight = FontWeight.ExtraBold
                        ),
                        color = Color(0xFFF9A825), // Dark Yellow / Gold පාට
                        modifier = Modifier
                            .scale(scale.value)
                            .alpha(alpha.value)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Your Smart Life Journal",
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color(0xFFF9A825).copy(alpha = 0.8f),
                        modifier = Modifier.alpha(alpha.value)
                    )

                    Spacer(modifier = Modifier.height(32.dp))


                    CircularProgressIndicator(
                        color = Color(0xFFF9A825),
                        modifier = Modifier.alpha(alpha.value)
                    )
                }
            }
        }

        // --- 3D STARRY BACKGROUND ANIMATION ---
        @Composable
        fun StarryBackground(scrollOffset: Float = 0f) {

            val stars = remember {
                List(60) {
                    listOf(
                        Math.random().toFloat(),
                        Math.random().toFloat(),
                        (Math.random() * 4 + 2).toFloat(),
                        (Math.random() * 0.8 + 0.2).toFloat()
                    )
                }
            }


            val infiniteTransition = rememberInfiniteTransition(label = "twinkle")
            val twinkleAlpha by infiniteTransition.animateFloat(
                initialValue = 0.2f,
                targetValue = 0.9f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ), label = "alpha"
            )

            Canvas(modifier = Modifier.fillMaxSize().background(Color(0xFF0A2540))) {
                stars.forEach { starParams ->
                    val x = starParams[0] * size.width
                    val originalY = starParams[1] * size.height
                    val radius = starParams[2]
                    val speed = starParams[3]


                    val yOffset = (scrollOffset * speed) % size.height
                    var finalY = originalY - yOffset
                    if (finalY < 0) finalY += size.height
                    if (finalY > size.height) finalY -= size.height

                    drawCircle(
                        color = Color(0xFFF9A825).copy(
                            alpha = twinkleAlpha * (speed + 0.2f).coerceAtMost(
                                1f
                            )
                        ),
                        radius = radius,
                        center = Offset(x, finalY)
                    )
                }
            }
        }


