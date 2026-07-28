package com.orbistrade.dubaiai

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orbistrade.dubaiai.capture.ScreenCaptureService
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.overlay.OverlayService

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationsIfNeeded()
        setContent {
            MaterialTheme {
                OrbisTradeApp()
            }
        }
    }

    private fun requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
    }
}

class MainViewModel : ViewModel() {
    val overlayRunning = AppRuntimeState.overlayRunning
    val captureRunning = AppRuntimeState.captureRunning
    val capturedFrames = AppRuntimeState.capturedFrames
}

@Composable
private fun OrbisTradeApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val routes = listOf("controle", "diagnostico")

    Scaffold(
        bottomBar = {
            NavigationBar {
                routes.forEach { route ->
                    NavigationBarItem(
                        selected = false,
                        onClick = { navController.navigate(route) },
                        icon = { Text(if (route == "controle") "◉" else "≡") },
                        label = { Text(route.replaceFirstChar(Char::uppercase)) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "controle",
            modifier = Modifier.padding(padding)
        ) {
            composable("controle") { ControlScreen(viewModel) }
            composable("diagnostico") { DiagnosticsScreen(viewModel) }
        }
    }
}

@Composable
private fun ControlScreen(viewModel: MainViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val overlayRunning by viewModel.overlayRunning.collectAsState()
    val captureRunning by viewModel.captureRunning.collectAsState()

    val captureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val data = result.data
        if (result.resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(activity, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            }
            ContextCompat.startForegroundService(activity, serviceIntent)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Orbis Trade AI", style = MaterialTheme.typography.headlineMedium)
        Text("Sprint 1 — infraestrutura de leitura em tela")

        StatusCard("Overlay", overlayRunning)
        StatusCard("Captura MediaProjection", captureRunning)

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (!Settings.canDrawOverlays(activity)) {
                    activity.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:${activity.packageName}")
                        )
                    )
                } else {
                    ContextCompat.startForegroundService(
                        activity,
                        Intent(activity, OverlayService::class.java)
                    )
                }
            }
        ) {
            Text(if (overlayRunning) "Overlay ativo" else "Autorizar e iniciar overlay")
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                val manager = activity.getSystemService(MediaProjectionManager::class.java)
                captureLauncher.launch(manager.createScreenCaptureIntent())
            }
        ) {
            Text(if (captureRunning) "Captura ativa" else "Iniciar captura da tela")
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { activity.stopService(Intent(activity, OverlayService::class.java)) }) {
                Text("Parar overlay")
            }
            Button(onClick = { activity.stopService(Intent(activity, ScreenCaptureService::class.java)) }) {
                Text("Parar captura")
            }
        }
    }
}

@Composable
private fun DiagnosticsScreen(viewModel: MainViewModel) {
    val overlayRunning by viewModel.overlayRunning.collectAsState()
    val captureRunning by viewModel.captureRunning.collectAsState()
    val frames by viewModel.capturedFrames.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Diagnóstico", style = MaterialTheme.typography.headlineMedium)
        StatusCard("Serviço de overlay", overlayRunning)
        StatusCard("Serviço de captura", captureRunning)
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Frames recebidos")
                Spacer(Modifier.height(4.dp))
                Text(frames.toString(), style = MaterialTheme.typography.headlineSmall)
            }
        }
        Text("O Sprint 2 usará esses frames para detectar gráfico e candles.")
    }
}

@Composable
private fun StatusCard(label: String, active: Boolean) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label)
            Text(if (active) "ATIVO" else "INATIVO")
        }
    }
}
