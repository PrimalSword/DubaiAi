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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.orbistrade.dubaiai.capture.ScreenCaptureService
import com.orbistrade.dubaiai.core.AppRuntimeState
import com.orbistrade.dubaiai.history.SignalHistoryStore
import com.orbistrade.dubaiai.lab.MarketMode
import com.orbistrade.dubaiai.overlay.OverlayService
import com.orbistrade.dubaiai.statistics.StatisticsEngine
import java.io.File
import java.text.DateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),2001)
        AppRuntimeState.updateHistory(SignalHistoryStore(this).use { it.recent() })
        setContent { MaterialTheme { OrbisTradeApp() } }
    }
}

class MainViewModel : ViewModel() {
    val overlayRunning=AppRuntimeState.overlayRunning; val captureRunning=AppRuntimeState.captureRunning
    val capturedFrames=AppRuntimeState.capturedFrames; val vision=AppRuntimeState.vision
    val history=AppRuntimeState.history; val marketMode=AppRuntimeState.marketMode
    fun setMode(mode:MarketMode)=AppRuntimeState.setMarketMode(mode)
}

@Composable private fun OrbisTradeApp(vm:MainViewModel= viewModel()) {
    val nav=rememberNavController(); val routes=listOf("controle","visao","sinais","dashboard")
    Scaffold(bottomBar={ NavigationBar { routes.forEach { route -> NavigationBarItem(false,{nav.navigate(route)},{Text(when(route){"controle"->"◉";"visao"->"◎";"sinais"->"⚡";else->"▦"})},label={Text(if(route=="dashboard")"Dados" else route.replaceFirstChar(Char::uppercase))}) } } }) { p ->
        NavHost(nav,"controle",Modifier.padding(p)) { composable("controle"){ControlScreen(vm)}; composable("visao"){VisionScreen(vm)}; composable("sinais"){SignalsScreen(vm)}; composable("dashboard"){DashboardScreen(vm)} }
    }
}

@Composable private fun ControlScreen(vm:MainViewModel) {
    val activity=androidx.compose.ui.platform.LocalContext.current as Activity
    val overlay by vm.overlayRunning.collectAsState(); val capture by vm.captureRunning.collectAsState(); val mode by vm.marketMode.collectAsState()
    val launcher=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r -> r.data?.takeIf{r.resultCode==Activity.RESULT_OK}?.let { d -> ContextCompat.startForegroundService(activity,Intent(activity,ScreenCaptureService::class.java).apply{putExtra(ScreenCaptureService.EXTRA_RESULT_CODE,r.resultCode);putExtra(ScreenCaptureService.EXTRA_RESULT_DATA,d)}) } }
    Page("Orbis Trade AI v1.0 RC1") {
        Text("Laboratório final de validação — somente conta demo")
        Text("Modo de mercado",style=MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) { Button(onClick={vm.setMode(MarketMode.OPEN_MARKET)},enabled=mode!=MarketMode.OPEN_MARKET){Text("Mercado aberto")}; Button(onClick={vm.setMode(MarketMode.OTC)},enabled=mode!=MarketMode.OTC){Text("OTC")} }
        MetricCard("Modo",if(mode==MarketMode.OTC)"OTC" else "MERCADO ABERTO"); StatusCard("Overlay",overlay); StatusCard("MediaProjection",capture)
        Button(onClick={
            if(!Settings.canDrawOverlays(activity)) activity.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,Uri.parse("package:${activity.packageName}")))
            else ContextCompat.startForegroundService(activity,Intent(activity,OverlayService::class.java))
        },modifier=Modifier.fillMaxWidth()){Text(if(overlay)"Overlay ativo" else "Autorizar e iniciar overlay")}
        Button(onClick={launcher.launch(activity.getSystemService(MediaProjectionManager::class.java).createScreenCaptureIntent())},modifier=Modifier.fillMaxWidth()){Text(if(capture)"Captura ativa" else "Iniciar captura e análise")}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={activity.stopService(Intent(activity,OverlayService::class.java))}){Text("Parar overlay")};Button(onClick={activity.stopService(Intent(activity,ScreenCaptureService::class.java))}){Text("Parar captura")}}
    }
}

@Composable private fun VisionScreen(vm:MainViewModel) {
    val frames by vm.capturedFrames.collectAsState(); val v by vm.vision.collectAsState(); val i=v.indicators; val s=v.strategy; val l=v.lab; val e=l.economics
    Page("Diagnóstico e validação RC1") {
        MetricCard("Frames",frames.toString()); MetricCard("Tela validada",yesNo(l.context.screenValidated)); MetricCard("Gráfico",if(v.graphDetected)"SIM (${(v.graphConfidence*100).toInt()}%)" else "NÃO")
        MetricCard("Mercado",l.context.mode.name); MetricCard("Ativo",l.context.asset); MetricCard("Payout",l.context.payoutPercent?.let{"%.1f%%".format(Locale.US,it)}?:"NÃO IDENTIFICADO"); MetricCard("Vencimento",l.context.expirySeconds?.let{"$it s"}?:"NÃO IDENTIFICADO")
        MetricCard("Candles rastreados",v.candleCount.toString()); MetricCard("Regime",l.regime.name); MetricCard("Tendência",i.trend); MetricCard("Volatilidade",i.volatility); MetricCard("Lateral",yesNo(i.lateral))
        MetricCard("Sinal",s.direction.name); MetricCard("Confluência","${s.score}/100 — ${s.confidence}"); MetricCard("Probabilidade",e.calibratedProbability?.let{"%.1f%%".format(Locale.US,it*100)}?:"SEM AMOSTRA")
        MetricCard("Break-even",e.breakEvenRate?.let{"%.1f%%".format(Locale.US,it*100)}?:"PAYOUT AUSENTE"); MetricCard("EV",e.expectedValue?.let{"%+.3f".format(Locale.US,it)}?:"INDETERMINADO"); MetricCard("Margem",e.edgePercentagePoints?.let{"%+.1f p.p.".format(Locale.US,it)}?:"INDETERMINADA")
        MetricCard("Amostra","${e.sampleSize} — ${e.confidenceLabel}"); MetricCard("Operação permitida",yesNo(l.operationAllowed)); MetricCard("Bloqueios",if(l.blockers.isEmpty())"Nenhum" else l.blockers.joinToString("\n• ",prefix="• "))
        MetricCard("Fundamentação",s.reason); MetricCard("EMA 12",fmt(i.ema12)); MetricCard("EMA 60",fmt(i.ema60)); MetricCard("ATR 14",fmt(i.atr14)); MetricCard("OCR",v.ocrText.ifBlank{"Aguardando..."}); v.error?.let{Text("Erro: $it")}
    }
}

@Composable private fun SignalsScreen(vm:MainViewModel) {
    val ctx=androidx.compose.ui.platform.LocalContext.current; val history by vm.history.collectAsState()
    Page("Auditoria de sinais") { if(history.isEmpty())Text("Nenhum sinal registrado."); history.forEach { item -> Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){Text("${item.direction} · ${item.score}/100 · ${item.marketMode}",style=MaterialTheme.typography.titleMedium);Text("${item.asset}\n${DateFormat.getDateTimeInstance().format(Date(item.timestamp))}\nRegime: ${item.regime}\nPayout: ${item.payout?:"—"}%\nResultado: ${item.outcome?:"PENDENTE"}\n${item.reason}");Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){listOf("WIN","LOSS","DRAW").forEach{o->Button(onClick={markOutcome(ctx,item.id,o)}){Text(o)}}};Row(horizontalArrangement=Arrangement.spacedBy(4.dp)){Button(onClick={markOutcome(ctx,item.id,"INVALIDATED")}){Text("Invalidar")};Button(onClick={markOutcome(ctx,item.id,null)}){Text("Limpar")}}}} } }
}

@Composable private fun DashboardScreen(vm:MainViewModel) {
    val ctx=androidx.compose.ui.platform.LocalContext.current; val history by vm.history.collectAsState(); val stats=StatisticsEngine.calculate(history); val otc=history.count{it.marketMode==MarketMode.OTC.name}; val open=history.count{it.marketMode==MarketMode.OPEN_MARKET.name}
    Page("Dashboard do laboratório") { MetricCard("Amostras separadas","OTC $otc · Mercado aberto $open");MetricCard("Sinais","${stats.totalSignals} · CALL ${stats.calls} · PUT ${stats.puts}");MetricCard("Resultados","WIN ${stats.wins} · LOSS ${stats.losses} · Pendentes ${stats.pending}");MetricCard("Win rate",if(stats.wins+stats.losses==0)"SEM RESULTADOS" else "%.1f%%".format(Locale.US,stats.winRate));MetricCard("Score médio","%.1f/100".format(Locale.US,stats.averageScore));MetricCard("Melhor horário",stats.bestHour?.let{"%02d:00".format(it)}?:"DADOS INSUFICIENTES");Text("Heatmap por horário",style=MaterialTheme.typography.titleLarge);stats.hourly.forEach{h->MetricCard("%02d:00".format(h.hour),"${h.total} sinais · ${h.wins}W/${h.losses}L")};Button(onClick={exportCsv(ctx)},modifier=Modifier.fillMaxWidth()){Text("Exportar auditoria completa em CSV")};Text("Não altere os parâmetros durante a semana de validação.") }
}

@Composable private fun Page(title:String,content:@Composable ColumnScope.()->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){Text(title,style=MaterialTheme.typography.headlineMedium);content()}}
private fun markOutcome(c:Context,id:Long,o:String?){SignalHistoryStore(c).use{it.setOutcome(id,o);AppRuntimeState.updateHistory(it.recent())}}
private fun exportCsv(c:Context){val f=File(c.cacheDir,"orbis_trade_rc1_auditoria.csv");f.writeText(SignalHistoryStore(c).use{it.csv()});val u=FileProvider.getUriForFile(c,"${c.packageName}.files",f);c.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/csv";putExtra(Intent.EXTRA_STREAM,u);addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)},"Exportar auditoria"))}
private fun fmt(v:Double?)=v?.let{"%.4f".format(Locale.US,it)}?:"AQUECENDO"; private fun yesNo(v:Boolean)=if(v)"SIM" else "NÃO"
@Composable private fun StatusCard(l:String,a:Boolean)=MetricCard(l,if(a)"ATIVO" else "INATIVO")
@Composable private fun MetricCard(l:String,v:String){Card(Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(3.dp)){Text(l);Text(v,style=MaterialTheme.typography.titleMedium)}}}
