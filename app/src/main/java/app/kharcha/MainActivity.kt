package app.kharcha

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val vm: ExpenseViewModel by viewModels()
    private var setup by mutableStateOf(SetupState(sms = false, notif = false, usage = false, overlay = false))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent { KharchaTheme { HomeScreen(vm, setup, onPermsChanged = { refresh() }) } }
    }

    // Coming back from Settings re-checks everything, so finished steps disappear.
    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        setup = Setup.state(this)
        Setup.startWatcherIfReady(this)
        if (setup.sms) vm.scanSms { }
    }
}

private fun smsPerms(): Array<String> {
    val base = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
    return if (Build.VERSION.SDK_INT >= 33) base + Manifest.permission.POST_NOTIFICATIONS else base
}

@Composable
fun HomeScreen(vm: ExpenseViewModel, setup: SetupState, onPermsChanged: () -> Unit) {
    val ctx = LocalContext.current
    val expenses by vm.expenses.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Expense?>(null) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermsChanged() }

    val monthStart = remember { startOfMonth() }
    val monthItems = expenses.filter { it.timestamp >= monthStart }
    val split = monthItems.groupBy { it.category }
        .map { (name, list) -> name to list.sumOf { it.amount } }
        .sortedByDescending { it.second }
    val days = expenses.groupBy { startOfDay(it.timestamp) }

    Scaffold(
        containerColor = Ink,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = Lime,
                contentColor = Ink,
                shape = RoundedCornerShape(20.dp)
            ) { Icon(Icons.Default.Add, contentDescription = "Add expense") }
        }
    ) { pad ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp, end = 20.dp,
                top = pad.calculateTopPadding() + 12.dp,
                bottom = pad.calculateBottomPadding() + 100.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Header(autoOn = setup.all) }
            item { HeroCard(monthItems.sumOf { it.amount }, expenses.sumOf { it.amount }, monthItems.size, split) }

            if (!setup.all) item {
                SetupCard(
                    setup,
                    onSms = { permLauncher.launch(smsPerms()) },
                    onNotif = { ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                    onUsage = { ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) },
                    onOverlay = {
                        ctx.startActivity(
                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${ctx.packageName}"))
                        )
                    }
                )
            }

            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Pill("↻  Rescan SMS", Modifier.weight(1f)) {
                        if (!setup.sms) permLauncher.launch(smsPerms())
                        else vm.scanSms { n ->
                            val msg = if (n == 0) "No new bank SMS" else "Added $n from SMS"
                            Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                    Pill("↗  Export CSV", Modifier.weight(1f)) {
                        vm.exportCsv { ctx.startActivity(Intent.createChooser(it, "Export expenses")) }
                    }
                }
            }

            if (expenses.isEmpty()) item { EmptyState() }

            days.forEach { (day, list) ->
                item(key = "day-$day") { DayHeader(dayLabel(day), list.sumOf { it.amount }) }
                items(list, key = { it.id }) { e -> TxnRow(e) { editing = e } }
            }
        }
    }

    if (showAdd) {
        AddSheet(
            onDismiss = { showAdd = false },
            onSave = { amount, category, note -> vm.addExpense(amount, category, note); showAdd = false }
        )
    }
    editing?.let { e ->
        EditDialog(
            e,
            onDismiss = { editing = null },
            onSave = { vm.updateExpense(it); editing = null },
            onDelete = { vm.deleteExpense(e); editing = null }
        )
    }
}

@Composable
fun Header(autoOn: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("kharcha", fontSize = 26.sp, fontWeight = FontWeight.Black, color = TextMain, letterSpacing = (-0.5).sp)
        Text(".", fontSize = 26.sp, fontWeight = FontWeight.Black, color = Lime)
        Spacer(Modifier.weight(1f))
        Row(
            Modifier.clip(RoundedCornerShape(50)).background(Panel).padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(if (autoOn) Lime else Coral))
            Spacer(Modifier.width(6.dp))
            Text(
                if (autoOn) "Auto-capture on" else "Setup needed",
                style = MaterialTheme.typography.labelMedium,
                color = if (autoOn) TextMain else Muted
            )
        }
    }
}

@Composable
fun HeroCard(monthTotal: Double, allTotal: Double, count: Int, split: List<Pair<String, Double>>) {
    val monthName = remember { SimpleDateFormat("MMMM", Locale.getDefault()).format(Date()) }
    val total = split.sumOf { it.second }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(Panel).padding(22.dp)) {
        Text("Spent in $monthName", style = MaterialTheme.typography.labelLarge, color = Muted)
        Spacer(Modifier.height(4.dp))
        Text(money(monthTotal), fontSize = 42.sp, fontWeight = FontWeight.Black, color = Lime, letterSpacing = (-1).sp)
        Text(
            "$count transactions · ${money(allTotal)} all time",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Spacer(Modifier.height(18.dp))

        // One bar, split by category colour.
        Row(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(Panel2)) {
            if (total > 0) split.forEach { (name, v) ->
                Box(
                    Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f))
                        .fillMaxHeight()
                        .background(cat(name).color)
                )
            }
        }

        if (total > 0) {
            Spacer(Modifier.height(12.dp))
            split.take(4).forEach { (name, v) ->
                val k = cat(name)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(k.color))
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "${k.emoji}  ${k.name}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMain,
                        modifier = Modifier.weight(1f)
                    )
                    val pct = v / total * 100
                    Text(if (pct < 1) "<1%" else "${pct.toInt()}%", style = MaterialTheme.typography.bodySmall, color = Muted)
                    Spacer(Modifier.width(12.dp))
                    Text(money(v), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = TextMain)
                }
            }
        }
    }
}

@Composable
fun SetupCard(s: SetupState, onSms: () -> Unit, onNotif: () -> Unit, onUsage: () -> Unit, onOverlay: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Panel).padding(20.dp)) {
        Text("Turn on auto-capture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
        Text(
            "${s.done} of 4 done. Kharcha logs spends by itself once these are on.",
            style = MaterialTheme.typography.bodySmall,
            color = Muted
        )
        Spacer(Modifier.height(12.dp))
        LinearProgressIndicator(
            progress = { s.done / 4f },
            color = Lime,
            trackColor = Panel2,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(8.dp))
        SetupRow("Read bank SMS", "Logs every debit alert", s.sms, onSms)
        SetupRow("Payment notifications", "GPay, PhonePe, Paytm, BHIM", s.notif, onNotif)
        SetupRow("Usage access", "Knows when you leave a payment app", s.usage, onUsage)
        SetupRow("Show over other apps", "For the “how much?” pop-up", s.overlay, onOverlay)
    }
}

@Composable
fun SetupRow(title: String, sub: String, done: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !done, onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(30.dp).clip(CircleShape).background(if (done) Lime else Panel2),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Default.Check, contentDescription = null, tint = Ink, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (done) Muted else TextMain)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = Muted)
        }
        Text(
            if (done) "On" else "Enable",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = if (done) Muted else Lime
        )
    }
}

@Composable
fun Pill(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = Panel, modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = TextMain,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp)
        )
    }
}

@Composable
fun DayHeader(label: String, total: Double) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 14.dp, bottom = 2.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = Muted, modifier = Modifier.weight(1f))
        Text(money(total), style = MaterialTheme.typography.labelLarge, color = Muted)
    }
}

@Composable
fun TxnRow(e: Expense, onClick: () -> Unit) {
    val k = cat(e.category)
    val time = remember(e.timestamp) { SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(e.timestamp)) }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Panel)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(k.color.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Text(k.emoji, fontSize = 20.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                e.merchant ?: e.note ?: k.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = TextMain,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SourceBadge(e.source)
                Spacer(Modifier.width(6.dp))
                Text(time, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
        }
        Text(
            "−" + money(e.amount),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = TextMain
        )
    }
}

@Composable
fun SourceBadge(source: String) {
    val manual = source == "manual"
    Text(
        if (manual) "Manual" else source,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        color = if (manual) Muted else Lime,
        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Panel2).padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

@Composable
fun EmptyState() {
    Column(Modifier.fillMaxWidth().padding(vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text("🧾", fontSize = 40.sp)
        Spacer(Modifier.height(8.dp))
        Text("No spends yet", style = MaterialTheme.typography.titleMedium, color = TextMain)
        Text("Tap + to add one, or let Kharcha catch them", style = MaterialTheme.typography.bodySmall, color = Muted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(onDismiss: () -> Unit, onSave: (Double, String, String?) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    val value = amount.toDoubleOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding()) {
            Text("New expense", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(16.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(14.dp))
            CategoryChips(category) { category = it }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = kharchaFieldColors(),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
            Button(
                onClick = { if (value != null && value > 0) onSave(value, category, note.ifBlank { null }) },
                enabled = value != null && value > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun EditDialog(e: Expense, onDismiss: () -> Unit, onSave: (Expense) -> Unit, onDelete: () -> Unit) {
    var category by remember(e.id) { mutableStateOf(e.category) }
    val whenText = remember(e.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(e.timestamp))
    }
    val origin = if (e.source == "manual") "Added by you" else "via ${e.source}"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text(money(e.amount), fontWeight = FontWeight.Black, color = TextMain) },
        text = {
            Column {
                Text(
                    listOfNotNull(e.merchant, origin, whenText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(14.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge, color = TextMain)
                Spacer(Modifier.height(8.dp))
                CategoryChips(category) { category = it }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(e.copy(category = category)) }) {
                Text("Save", color = Lime, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = Coral) }
        }
    )
}

fun startOfMonth(): Long = Calendar.getInstance().apply {
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
    timeInMillis = t
    set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
}.timeInMillis

fun dayLabel(day: Long): String {
    val today = startOfDay(System.currentTimeMillis())
    return when (day) {
        today -> "Today"
        today - 86_400_000L -> "Yesterday"
        else -> SimpleDateFormat("EEE, dd MMM", Locale.getDefault()).format(Date(day))
    }
}
