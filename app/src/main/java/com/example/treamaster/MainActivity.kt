package com.example.treamaster

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.treamaster.ui.home.HomeScreen
import com.example.treamaster.ui.measure.MeasureScreen
import com.example.treamaster.ui.permission.PermissionGate
import com.example.treamaster.ui.theme.TreaMasterTheme
import com.example.treamaster.ui.upload.BatchScreen
import com.example.treamaster.ui.upload.UploadDetailScreen
import com.example.treamaster.ui.upload.UploadListScreen
import com.example.treamaster.ui.upload.UploadViewModel

private sealed interface Screen {
    data object Home : Screen
    data object Measure : Screen
    data object UploadList : Screen
    data class UploadBatch(val batchId: String) : Screen
    data class UploadDetail(val id: Long, val batchId: String) : Screen
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TreaMasterTheme {
                var screen by remember { mutableStateOf<Screen>(Screen.Home) }
                val uploadVm: UploadViewModel = viewModel()

                when (val s = screen) {
                    Screen.Home -> HomeScreen(
                        onUpload = { screen = Screen.UploadList },
                        onMeasure = { screen = Screen.Measure },
                    )
                    // Only measuring needs the camera, so permissions are asked here, not at launch.
                    Screen.Measure -> PermissionGate { permissions ->
                        MeasureScreen(
                            locationGranted = permissions.locationGranted,
                            onBack = { screen = Screen.Home },
                            onFinished = { screen = Screen.UploadList },
                        )
                    }
                    Screen.UploadList -> UploadListScreen(
                        vm = uploadVm,
                        onBack = { screen = Screen.Home },
                        onOpen = { screen = Screen.UploadBatch(it) },
                    )
                    is Screen.UploadBatch -> BatchScreen(
                        vm = uploadVm,
                        batchId = s.batchId,
                        onBack = { screen = Screen.UploadList },
                        onOpen = { screen = Screen.UploadDetail(it, s.batchId) },
                    )
                    is Screen.UploadDetail -> UploadDetailScreen(
                        vm = uploadVm,
                        id = s.id,
                        onBack = { screen = Screen.UploadBatch(s.batchId) },
                    )
                }
            }
        }
    }
}
