package com.orbistrade.dubaiai

import android.Manifest
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.orbistrade.dubaiai.capture.ScreenCaptureService
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.history.SignalHistoryStore
import com.orbistrade.dubaiai.overlay.OverlayService
import com.orbistrade.dubaiai.statistics.StatisticsEngine
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        AppRuntimeState.updateHistory(SignalHistoryStore(this).use { it.recent() })
        setContent { MaterialTheme { OrbisTradeApp() } }
    }
}

class MainViewModel : ViewModel() {
    val overlayRunning = AppRuntimeState.overlayRunning
    val captureRunning = AppRuntimeState.captureRunning
    val capturedFrames = AppRuntimeState.capturedFrames
    val vision = AppRuntimeState.vision
    val history = AppRuntimeState.history
}

@Composable
private fun OrbisTradeApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val routes = listOf("controle", "visao", "sinais", "dashboard")
    Scaffold(bottomBar = {
        NavigationBar {
            routes.forEach { route ->
                NavigationBarItem(
                    selected = false,
                    onClick = { navController.navigate(route) },
                    icon = { Text(when (route) { "controle" -> "◉"; "visao" -> "◎"; "sinais" -> "⚡"; else -> "▦" }) },
                    label = { Text(if (route == "dashboard") "Dados" else route.replaceFirstChar(Char::uppercase)) }
                )
            }
        }
    }) { padding ->
        NavHost(navController, "controle", Modifier.padding(padding)) {
            composable("controle") { ControlScreen(viewModel) }
            composable("visao") { VisionScreen(viewModel) }
            composable("sinais") { SignalsScreen(viewModel) }
            composable("dashboard") { DashboardScreen(viewModel) }
        }
    }
}

@Composable
private fun ControlScreen(viewModel: MainViewModel) {
    val activity = androidx.compose.ui.platform.LocalContext.current as Activity
    val overlayRunning by viewModel.overlayRunning.collectAsState()
    val captureRunning by viewModel.captureRunning.collectAsState()
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { data ->
            ContextCompat.startForegroundService(activity, Intent(activity, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, data)
            })
        }
    }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Orbis Trade AI", style = MaterialTheme.typography.headlineMedium)
        Text("Sprint 5 — estratégia, validação e estatísticas")
        StatusCard("Overlay", overlayRunning)
        StatusCard("MediaProjection", captureRunning)
        Button(Modifier.fillMaxWidth(), onClick = {
            if (!Settings.canDrawOverlays(activity)) activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${activity.packageName}")))
            else ContextCompat.startForegroundService(activity, Intent(activity, OverlayService::class.java))
        }) { Text(if (overlayRunning) "Overlay ativo" else "Autorizar e iniciar overlay") }
        Button(Modifier.fillMaxWidth(), onClick = {
            captureLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())
        }) { Text(if (captureRunning) "Captura ativa" else "Iniciar captura e análise") }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = { activity.stopService(Intent(activity, OverlayService::class.java)) }) { Text("Parar overlay") }
            Button(onClick = { activity.stopService(Intent(activity, ScreenCaptureService::class.java)) }) { Text("Parar captura") }
        }
    }
}

@Composable
private fun VisionScreen(viewModel: MainViewModel) {
    val frames by viewModel.capturedFrames.collectAsState()
    val vision by viewModel.vision.collectAsState()
    val indicators = vision.indicators
    val strategy = vision.strategy
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico, indicadores e estratégia", style = MaterialTheme.typography.headlineMedium)
        MetricCard("Frames capturados", frames.toString())
        MetricCard("Gráfico detectado", if (vision.graphDetected) "SIM (${(vision.graphConfidence * 100).toInt()}%)" else "NÃO")
        MetricCard("Candles reconstruídos", vision.candleCount.toString())
        MetricCard("Tendência", indicators.trend)
        MetricCard("Volatilidade", indicators.volatility)
        MetricCard("Lateralidade", if (indicators.lateral) "SIM" else "NÃO")
        MetricCard("Sinal Dubai V1", strategy.direction.name)
        MetricCard("Score", "${strategy.score}/100 — ${strategy.confidence}")
        MetricCard("Fundamentação", strategy.reason)
        MetricCard("EMA 12", format(indicators.ema12))
        MetricCard("EMA 60", format(indicators.ema60))
        MetricCard("Bollinger 12 / 1,5", "${format(indicators.bollingerLower)} | ${format(indicators.bollingerMiddle)} | ${format(indicators.bollingerUpper)}")
        MetricCard("ATR 14", format(indicators.atr14))
        MetricCard("OCR", vision.ocrText.ifBlank { "Aguardando texto..." })
        vision.error?.let { Text("Erro: $it") }
    }
}

@Composable
private fun SignalsScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by viewModel.history.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Histórico local de sinais", style = MaterialTheme.typography.headlineMedium)
        if (history.isEmpty()) Text("Nenhum sinal acionável registrado.")
        history.forEach { item ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${item.direction} · ${item.score}/100 · ${item.confidence}", style = MaterialTheme.typography.titleMedium)
                    Text("${item.asset}\n${DateFormat.getDateTimeInstance().format(Date(item.timestamp))}\n${item.reason}")
                    Text("Resultado: ${item.outcome ?: "PENDENTE"}")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { markOutcome(context, item.id, "WIN") }) { Text("WIN") }
                        Button(onClick = { markOutcome(context, item.id, "LOSS") }) { Text("LOSS") }
                        Button(onClick = { markOutcome(context, item.id, null) }) { Text("Limpar") }
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: MainViewModel) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val history by viewModel.history.collectAsState()
    val stats = StatisticsEngine.calculate(history)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Dashboard do laboratório", style = MaterialTheme.typography.headlineMedium)
        MetricCard("Sinais", "${stats.totalSignals} · CALL ${stats.calls} · PUT ${stats.puts}")
        MetricCard("Resultados", "WIN ${stats.wins} · LOSS ${stats.losses} · Pendentes ${stats.pending}")
        MetricCard("Win rate", if (stats.wins + stats.losses == 0) "SEM RESULTADOS" else String.format(Locale.US, "%.1f%%", stats.winRate))
        MetricCard("Score médio", String.format(Locale.US, "%.1f/100", stats.averageScore))
        MetricCard("Melhor horário", stats.bestHour?.let { "%02d:00".format(it) } ?: "DADOS INSUFICIENTES")
        Text("Heatmap por horário", style = MaterialTheme.typography.titleLarge)
        if (stats.hourly.isEmpty()) Text("Ainda não há sinais para o heatmap.")
        stats.hourly.forEach { hour ->
            val rate = if (hour.wins + hour.losses == 0) "—" else String.format(Locale.US, "%.0f%%", hour.winRate)
            MetricCard("%02d:00".format(hour.hour), "${hour.total} sinais · ${hour.wins}W/${hour.losses}L · $rate")
        }
        Button(Modifier.fillMaxWidth(), onClick = { exportCsv(context) }) { Text("Exportar histórico em CSV") }
        Text("Win/loss é informado manualmente após conferir o resultado na conta demo.")
    }
}

private fun markOutcome(context: Context, id: Long, outcome: String?) {
    SignalHistoryStore(context).use { store ->
        store.setOutcome(id, outcome)
        AppRuntimeState.updateHistory(store.recent())
    }
}

private fun exportCsv(context: Context) {
    val file = File(context.cacheDir, "orbis_trade_sinais.csv")
    file.writeText(SignalHistoryStore(context).use { it.csv() })
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/csv"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }, "Exportar sinais"))
}

private fun format(value: Double?): String = value?.let { String.format(Locale.US, "%.2f", it) } ?: "AQUECENDO"

@Composable
private fun StatusCard(label: String, active: Boolean) = MetricCard(label, if (active) "ATIVO" else "INATIVO")

@Composable
private fun MetricCard(label: String, value: String) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}
