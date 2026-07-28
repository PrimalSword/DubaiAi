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
import androidx.compose.material3.Scaffold
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
import com.orbistrade.dubaiai.lab.MarketMode
import com.orbistrade.dubaiai.overlay.OverlayService
import com.orbistrade.dubaiai.statistics.StatisticsEngine
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 2001)
        }
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
    val marketMode = AppRuntimeState.marketMode
    fun setMode(mode: MarketMode) = AppRuntimeState.setMarketMode(mode)
}

@Composable
private fun OrbisTradeApp(viewModel: MainViewModel = viewModel()) {
    val navController = rememberNavController()
    val routes = listOf("controle", "visao", "sinais", "dashboard")
    Scaffold(bottomBar = {
        NavigationBar {
            routes.forEach { route ->
                NavigationBarItem(selected = false, onClick = { navController.navigate(route) }, icon = { Text(when(route){"controle"->"◉";"visao"->"◎";"sinais"->"⚡";else->"▦"}) }, label = { Text(if(route=="dashboard") "Dados" else route.replaceFirstChar(Char::uppercase)) })
            }
        }
    }) { padding ->
        NavHost(navController,"controle",Modifier.padding(padding)) {
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
    val mode by viewModel.marketMode.collectAsState()
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        result.data?.takeIf { result.resultCode == Activity.RESULT_OK }?.let { data ->
            ContextCompat.startForegroundService(activity, Intent(activity, ScreenCaptureService::class.java).apply {
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,result.resultCode); putExtra(ScreenCaptureService.EXTRA_RESULT_DATA,data)
            })
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Text("Orbis Trade AI v1.0 RC1", style=MaterialTheme.typography.headlineMedium)
        Text("Laboratório final de validação — uso exclusivo em conta demo")
        Text("Modo de mercado", style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={viewModel.setMode(MarketMode.OPEN_MARKET)}, enabled=mode!=MarketMode.OPEN_MARKET){Text("Mercado aberto")}
            Button(onClick={viewModel.setMode(MarketMode.OTC)}, enabled=mode!=MarketMode.OTC){Text("OTC")}
        }
        MetricCard("Modo selecionado", if(mode==MarketMode.OTC) "OTC" else "MERCADO ABERTO")
        StatusCard("Overlay",overlayRunning); StatusCard("MediaProjection",captureRunning)
        Button(Modifier.fillMaxWidth(),onClick={
            if(!Settings.canDrawOverlays(activity)) activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${activity.packageName}")))
            else ContextCompat.startForegroundService(activity,Intent(activity,OverlayService::class.java))
        }){Text(if(overlayRunning)"Overlay ativo" else "Autorizar e iniciar overlay")}
        Button(Modifier.fillMaxWidth(),onClick={captureLauncher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())}){Text(if(captureRunning)"Captura ativa" else "Iniciar captura e análise")}
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Button(onClick={activity.stopService(Intent(activity,OverlayService::class.java))}){Text("Parar overlay")}
            Button(onClick={activity.stopService(Intent(activity,ScreenCaptureService::class.java))}){Text("Parar captura")}
        }
    }
}

@Composable
private fun VisionScreen(viewModel: MainViewModel) {
    val frames by viewModel.capturedFrames.collectAsState(); val vision by viewModel.vision.collectAsState()
    val i=vision.indicators; val s=vision.strategy; val lab=vision.lab; val e=lab.economics
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Diagnóstico e validação RC1",style=MaterialTheme.typography.headlineMedium)
        MetricCard("Frames capturados",frames.toString())
        MetricCard("Tela validada",if(lab.context.screenValidated)"SIM" else "NÃO")
        MetricCard("Gráfico",if(vision.graphDetected)"SIM (${(vision.graphConfidence*100).toInt()}%)" else "NÃO")
        MetricCard("Mercado",lab.context.mode.name)
        MetricCard("Ativo",lab.context.asset)
        MetricCard("Payout",lab.context.payoutPercent?.let{"%.1f%%".format(Locale.US,it)}?:"NÃO IDENTIFICADO")
        MetricCard("Vencimento",lab.context.expirySeconds?.let{"$it s"}?:"NÃO IDENTIFICADO")
        MetricCard("Candles rastreados",vision.candleCount.toString())
        MetricCard("Regime",lab.regime.name)
        MetricCard("Tendência",i.trend); MetricCard("Volatilidade",i.volatility); MetricCard("Lateralidade",if(i.lateral)"SIM" else "NÃO")
        MetricCard("Sinal Dubai V1",s.direction.name); MetricCard("Confluência técnica","${s.score}/100 — ${s.confidence}")
        MetricCard("Probabilidade calibrada",e.calibratedProbability?.let{"%.1f%%".format(Locale.US,it*100)}?:"SEM AMOSTRA")
        MetricCard("Break-even",e.breakEvenRate?.let{"%.1f%%".format(Locale.US,it*100)}?:"PAYOUT AUSENTE")
        MetricCard("Valor esperado",e.expectedValue?.let{"%+.3f".format(Locale.US,it)}?:"INDETERMINADO")
        MetricCard("Margem",e.edgePercentagePoints?.let{"%+.1f p.p.".format(Locale.US,it)}?:"INDETERMINADA")
        MetricCard("Amostra","${e.sampleSize} — ${e.confidenceLabel}")
        MetricCard("Operação permitida",if(lab.operationAllowed)"SIM" else "NÃO")
        MetricCard("Bloqueios",if(lab.blockers.isEmpty())"Nenhum" else lab.blockers.joinToString("\n• ",prefix="• "))
        MetricCard("Fundamentação",s.reason)
        MetricCard("EMA 12",format(i.ema12)); MetricCard("EMA 60",format(i.ema60)); MetricCard("ATR 14",format(i.atr14))
        MetricCard("OCR",vision.ocrText.ifBlank{"Aguardando texto..."}); vision.error?.let{Text("Erro: $it")}
    }
}

@Composable
private fun SignalsScreen(viewModel: MainViewModel) {
    val context=androidx.compose.ui.platform.LocalContext.current; val history by viewModel.history.collectAsState()
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Auditoria de sinais",style=MaterialTheme.typography.headlineMedium)
        if(history.isEmpty()) Text("Nenhum sinal registrado.")
        history.forEach { item -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("${item.direction} · ${item.score}/100 · ${item.marketMode}",style=MaterialTheme.typography.titleMedium)
            Text("${item.asset}\n${DateFormat.getDateTimeInstance().format(Date(item.timestamp))}\nRegime: ${item.regime}\nPayout: ${item.payout?:"—"}%\nResultado: ${item.outcome?:"PENDENTE"}\n${item.reason}")
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(onClick={markOutcome(context,item.id,"WIN")}){Text("WIN")}; Button(onClick={markOutcome(context,item.id,"LOSS")}){Text("LOSS")}; Button(onClick={markOutcome(context,item.id,"DRAW")}){Text("DRAW")}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                Button(onClick={markOutcome(context,item.id,"INVALIDATED")}){Text("Invalidar")}; Button(onClick={markOutcome(context,item.id,null)}){Text("Limpar")}
            }
        } }
        }
    }
}

@Composable
private fun DashboardScreen(viewModel: MainViewModel) {
    val context=androidx.compose.ui.platform.LocalContext.current; val history by viewModel.history.collectAsState(); val stats=StatisticsEngine.calculate(history)
    val otc=history.filter{it.marketMode==MarketMode.OTC.name}; val open=history.filter{it.marketMode==MarketMode.OPEN_MARKET.name}
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text("Dashboard do laboratório",style=MaterialTheme.typography.headlineMedium)
        MetricCard("Amostras separadas","OTC ${otc.size} · Mercado aberto ${open.size}")
        MetricCard("Sinais","${stats.totalSignals} · CALL ${stats.calls} · PUT ${stats.puts}")
        MetricCard("Resultados","WIN ${stats.wins} · LOSS ${stats.losses} · Pendentes ${stats.pending}")
        MetricCard("Win rate",if(stats.wins+stats.losses==0)"SEM RESULTADOS" else "%.1f%%".format(Locale.US,stats.winRate))
        MetricCard("Score médio","%.1f/100".format(Locale.US,stats.averageScore))
        MetricCard("Melhor horário",stats.bestHour?.let{"%02d:00".format(it)}?:"DADOS INSUFICIENTES")
        Text("Heatmap por horário",style=MaterialTheme.typography.titleLarge)
        stats.hourly.forEach { h -> MetricCard("%02d:00".format(h.hour),"${h.total} sinais · ${h.wins}W/${h.losses}L · ${if(h.wins+h.losses==0)"—" else "%.0f%%".format(Locale.US,h.winRate)}") }
        Button(Modifier.fillMaxWidth(),onClick={exportCsv(context)}){Text("Exportar auditoria completa em CSV")}
        Text("RC1: resultados continuam sujeitos à conferência em conta demo durante a semana de validação.")
    }
}

private fun markOutcome(context:Context,id:Long,outcome:String?){SignalHistoryStore(context).use{it.setOutcome(id,outcome);AppRuntimeState.updateHistory(it.recent())}}
private fun exportCsv(context:Context){val file=File(context.cacheDir,"orbis_trade_rc1_auditoria.csv");file.writeText(SignalHistoryStore(context).use{it.csv()});val uri=FileProvider.getUriForFile(context,"${context.packageName}.files",file);context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_STREAM,uri);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Exportar auditoria"))}
private fun format(v:Double?)=v?.let{"%.4f".format(Locale.US,it)}?:"AQUECENDO"
@Composable private fun StatusCard(label:String,active:Boolean)=MetricCard(label,if(active)"ATIVO" else "INATIVO")
@Composable private fun MetricCard(label:String,value:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)){Text(label);Text(value,style=MaterialTheme.typography.titleMedium)}}}
