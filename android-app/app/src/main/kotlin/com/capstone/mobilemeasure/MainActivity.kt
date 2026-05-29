package com.capstone.mobilemeasure

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.capstone.mobilemeasure.permission.PermissionHelper
import com.capstone.mobilemeasure.ui.DebugLogScreen
import com.capstone.mobilemeasure.ui.MeasureScreen

private val ScreenBg = Color(0xFFF6F7FB)
private val Ink = Color(0xFF111827)

private enum class Screen { Measure, DebugLog }

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 사용자가 측정 시작을 누를 때 ViewModel이 권한을 다시 검사함 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!PermissionHelper.hasAllPermissions(this)) {
            permissionLauncher.launch(PermissionHelper.requiredPermissions())
        }

        setContent {
            MaterialTheme {
                AppRoot(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppRoot(viewModel: MainViewModel) {
    val state by viewModel.state.collectAsState()
    var screen by rememberSaveable { mutableStateOf(Screen.Measure) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (screen) {
                            Screen.Measure -> "실측 데이터 수집"
                            Screen.DebugLog -> "디버그 로그"
                        },
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = Ink,
                    )
                },
                navigationIcon = {
                    if (screen != Screen.Measure) {
                        IconButton(onClick = { screen = Screen.Measure }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "뒤로",
                                tint = Ink,
                            )
                        }
                    }
                },
                actions = {
                    if (screen == Screen.Measure) {
                        IconButton(onClick = { screen = Screen.DebugLog }) {
                            Icon(
                                Icons.Filled.BugReport,
                                contentDescription = "디버그 로그",
                                tint = Ink,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = ScreenBg,
                ),
            )
        },
        containerColor = ScreenBg,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(ScreenBg)
                .padding(padding),
        ) {
            when (screen) {
                Screen.Measure -> MeasureScreen(
                    state = state,
                    arSessionManager = viewModel.arSessionManager,
                    onStart = viewModel::startMeasuring,
                    onStop = viewModel::stopMeasuring,
                    onMarkIssue = viewModel::markIssue,
                    onUpload = viewModel::onUploadRequested,
                    onCalibrationFieldChange = { x, y, h ->
                        viewModel.onCalibrationFieldChange(
                            startFloorX = x,
                            startFloorY = y,
                            initialHeadingDeg = h,
                        )
                    },
                    onRefreshContext = viewModel::refreshContext,
                    onCalibrationPickedFromMap = viewModel::onCalibrationPickedFromMap,
                    onScannedToken = viewModel::onScannedToken,
                    onScanError = viewModel::onScanError,
                    onScanInstallProgress = viewModel::onScanInstallProgress,
                    onDismissError = viewModel::dismissError,
                )
                Screen.DebugLog -> DebugLogScreen(logs = state.recentLogs)
            }
        }
    }
}
