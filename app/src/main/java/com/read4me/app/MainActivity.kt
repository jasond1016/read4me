package com.read4me.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.read4me.app.data.StoryRepository
import com.read4me.app.model.StoryBook
import com.read4me.app.ui.LibraryScreen
import com.read4me.app.ui.Read4MeTheme
import com.read4me.app.ui.RecordingScreen
import com.read4me.app.ui.RerecordScreen
import com.read4me.app.ui.RecaptureScreen
import com.read4me.app.ui.ReviewScreen
import com.read4me.app.ui.SetupScreen
import com.read4me.app.ui.ChildReadingScreen

class MainActivity : ComponentActivity() {
    private sealed interface Destination {
        data object Library : Destination
        data object Setup : Destination
        data object ChildReading : Destination
        data class Recording(val title: String) : Destination
        data class Review(val book: StoryBook) : Destination
        data class Rerecord(val book: StoryBook, val ordinal: Int) : Destination
        data class Recapture(val book: StoryBook, val ordinal: Int) : Destination
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val repository = StoryRepository(this)
        setContent {
            Read4MeTheme {
                var destination: Destination by remember { mutableStateOf(Destination.Library) }
                var books by remember { mutableStateOf(repository.loadAll()) }
                var permissionTarget: Destination? by remember { mutableStateOf(null) }
                var permissionMessage by remember { mutableStateOf<String?>(null) }

                val permissionLauncher = rememberLauncher { granted ->
                    if (granted) {
                        permissionMessage = null
                        destination = permissionTarget ?: Destination.Library
                        permissionTarget = null
                    } else {
                        permissionMessage = "需要摄像头和麦克风权限，才能记录书面与陪读声音。"
                    }
                }

                when (val current = destination) {
                    Destination.Library -> LibraryScreen(
                        books = books,
                        onCreateBook = {
                            permissionMessage = null
                            destination = Destination.Setup
                        },
                        onChildMode = {
                            if (hasCameraPermission()) {
                                destination = Destination.ChildReading
                            } else {
                                permissionTarget = Destination.ChildReading
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA),
                                )
                            }
                        },
                        onOpenBook = { destination = Destination.Review(it) },
                    )

                    Destination.ChildReading -> ChildReadingScreen(
                        books = books,
                        onExit = { destination = Destination.Library },
                    )

                    Destination.Setup -> SetupScreen(
                        message = permissionMessage,
                        onBack = { destination = Destination.Library },
                        onStart = { title ->
                            if (hasCapturePermissions()) {
                                destination = Destination.Recording(title)
                            } else {
                                permissionTarget = Destination.Recording(title)
                                permissionLauncher.launch(
                                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                                )
                            }
                        },
                    )

                    is Destination.Recording -> RecordingScreen(
                        title = current.title,
                        repository = repository,
                        onCancel = {
                            destination = Destination.Library
                        },
                        onFinished = { book ->
                            books = repository.loadAll()
                            destination = Destination.Review(book)
                        },
                    )

                    is Destination.Review -> ReviewScreen(
                        book = current.book,
                        repository = repository,
                        onRerecord = { updatedBook, ordinal ->
                            val target = Destination.Rerecord(updatedBook, ordinal)
                            if (hasAudioPermission()) {
                                destination = target
                            } else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO))
                            }
                        },
                        onRecapture = { updatedBook, ordinal ->
                            val target = Destination.Recapture(updatedBook, ordinal)
                            if (hasCameraPermission()) destination = target
                            else {
                                permissionTarget = target
                                permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA))
                            }
                        },
                        onBack = {
                            books = repository.loadAll()
                            destination = Destination.Library
                        },
                    )

                    is Destination.Rerecord -> RerecordScreen(
                        book = current.book,
                        ordinal = current.ordinal,
                        repository = repository,
                        onCancel = { destination = Destination.Review(current.book) },
                        onFinished = { updated ->
                            books = repository.loadAll()
                            destination = Destination.Review(updated)
                        },
                    )

                    is Destination.Recapture -> RecaptureScreen(
                        book = current.book,
                        ordinal = current.ordinal,
                        repository = repository,
                        onCancel = { destination = Destination.Review(current.book) },
                        onFinished = { updated ->
                            books = repository.loadAll()
                            destination = Destination.Review(updated)
                        },
                    )
                }
            }
        }
    }

    @androidx.compose.runtime.Composable
    private fun rememberLauncher(onResult: (Boolean) -> Unit) =
        androidx.activity.compose.rememberLauncherForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { results -> onResult(results.values.all { it }) }

    private fun hasCapturePermissions(): Boolean =
        hasCameraPermission() &&
            hasAudioPermission()

    private fun hasCameraPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
}
