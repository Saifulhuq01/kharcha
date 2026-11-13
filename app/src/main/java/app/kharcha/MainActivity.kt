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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.text.SimpleDateFormat
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
    var missing by remember { mutableStateOf<List<SmsCandidate>?>(null) }
    val openMonths = remember { mutableStateMapOf<Long, Boolean>() }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { onPermsChanged() }

    val months = groupByMonth(expenses)
    val thisMonth = startOfMonth()
    val current = months.firstOrNull { it.first == thisMonth }?.second.orEmpty()
    val past = months.filter { it.first != thisMonth }

    fun rescan() {
        if (!setup.sms) {
            permLauncher.launch(smsPerms())
            return
        }
        vm.findMissingSms { found ->
            if (found.isEmpty()) Toast.makeText(ctx, "Every bank SMS is already in Kharcha", Toast.LENGTH_SHORT).show()
            else missing = found
        }
    }

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
            item {
                HeroCard(current.sumOf { it.total }, expenses.sumOf { it.total }, current.size, categorySplit(current))
            }

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
                    Pill("↻  Rescan SMS", Modifier.weight(1f)) { rescan() }
                    Pill("↗  Export CSV", Modifier.weight(1f)) {
                        vm.exportCsv { ctx.startActivity(Intent.createChooser(it, "Export expenses")) }
                    }
                }
            }

            // This month, in full.
            when {
                expenses.isEmpty() -> item { EmptyState() }
                current.isEmpty() -> item { QuietNote("Nothing spent this month yet") }
                else -> dayGroups(current, keyPrefix = "now") { editing = it }
            }

            // Earlier months, each folded into one card until tapped.
            if (past.isNotEmpty()) {
                item { SectionTitle("Earlier months") }
                past.forEach { (month, list) ->
                    val open = openMonths[month] == true
                    item(key = "month-$month") {
                        MonthCard(month, list, open) { openMonths[month] = !open }
                    }
                    if (open) dayGroups(list, keyPrefix = "m$month") { editing = it }
                }
            }
        }
    }

    if (showAdd) {
        AddSheet(
            onDismiss = { showAdd = false },
            onSave = { amount, category, note, time ->
                vm.addExpense(amount, category, note, time)
                showAdd = false
            }
        )
    }
    missing?.let { found ->
        RescanSheet(
            found,
            onDismiss = { missing = null },
            onAdd = { picked ->
                vm.addFromSms(picked)
                missing = null
                Toast.makeText(ctx, "Added ${picked.size} from SMS", Toast.LENGTH_SHORT).show()
            }
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

// Spends grouped under day headers ("Today", "Yesterday", "Wed, 11 Nov").
fun LazyListScope.dayGroups(list: List<Expense>, keyPrefix: String, onTap: (Expense) -> Unit) {
    list.groupBy { startOfDay(it.timestamp) }.forEach { (day, items) ->
        item(key = "$keyPrefix-day-$day") { DayHeader(dayLabel(day), items.sumOf { it.total }) }
        items(items, key = { it.id }) { e -> TxnRow(e) { onTap(e) } }
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
        SplitBar(split, 10.dp)
        if (split.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            CategoryLegend(split.take(4))
        }
    }
}

// One bar, split by category colour.
@Composable
fun SplitBar(split: List<Pair<String, Double>>, height: Dp) {
    val total = split.sumOf { it.second }
    Row(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height / 2)).background(Panel2)) {
        if (total > 0) split.forEach { (name, v) ->
            Box(
                Modifier.weight((v / total).toFloat().coerceAtLeast(0.01f))
                    .fillMaxHeight()
                    .background(cat(name).color)
            )
        }
    }
}

@Composable
fun CategoryLegend(split: List<Pair<String, Double>>) {
    val total = split.sumOf { it.second }.coerceAtLeast(0.01)
    split.forEach { (name, v) ->
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

// A past month folded into one card. Tap to open its breakdown and list.
@Composable
fun MonthCard(month: Long, list: List<Expense>, open: Boolean, onToggle: () -> Unit) {
    val split = categorySplit(list)
    Column(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Panel)
            .clickable(onClick = onToggle)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(monthLabel(month), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
                Text("${list.size} spends", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            Text(money(list.sumOf { it.total }), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.width(6.dp))
            Icon(
                if (open) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (open) "Collapse" else "Expand",
                tint = Muted
            )
        }
        Spacer(Modifier.height(12.dp))
        SplitBar(split, 6.dp)
        if (open) {
            Spacer(Modifier.height(10.dp))
            CategoryLegend(split)
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = Muted,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 2.dp)
    )
}

@Composable
fun QuietNote(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp)
    )
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
        Column(horizontalAlignment = Alignment.End) {
            Text(
                "−" + money(e.total),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextMain
            )
            // Show the split when something was added on top, e.g. "₹160 + ₹100".
            if (e.extra > 0) {
                Text(
                    "${money(e.amount)} + ${money(e.extra)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Lime
                )
            }
        }
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
fun AddSheet(onDismiss: () -> Unit, onSave: (Double, String, String?, Long) -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    var note by remember { mutableStateOf("") }
    val today = remember { startOfDay(System.currentTimeMillis()) }
    var day by remember { mutableStateOf(today) }
    var pickingDate by remember { mutableStateOf(false) }
    val value = amount.toDoubleOrNull()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 28.dp).imePadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "New expense",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextMain,
                    modifier = Modifier.weight(1f)
                )
                Surface(onClick = { pickingDate = true }, shape = RoundedCornerShape(12.dp), color = Panel2) {
                    Text(
                        "📅  ${dayLabel(day)}",
                        style = MaterialTheme.typography.labelLarge,
                        color = TextMain,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
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
                onClick = {
                    if (value != null && value > 0) {
                        // Today keeps the real time; a past day is filed at midday.
                        val time = if (day == today) System.currentTimeMillis() else day + 12 * 3_600_000L
                        onSave(value, category, note.ifBlank { null }, time)
                    }
                },
                enabled = value != null && value > 0,
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }

    if (pickingDate) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = localDayToUtcDate(day),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcDateToLocalDay(utcTimeMillis) <= today
            }
        )
        val colors = DatePickerDefaults.colors(
            containerColor = Panel,
            selectedDayContainerColor = Lime,
            selectedDayContentColor = Ink,
            todayDateBorderColor = Lime,
            todayContentColor = Lime
        )
        DatePickerDialog(
            onDismissRequest = { pickingDate = false },
            colors = colors,
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { day = utcDateToLocalDay(it) }
                    pickingDate = false
                }) { Text("OK", color = Lime, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { pickingDate = false }) { Text("Cancel", color = Muted) } }
        ) { DatePicker(state = state, colors = colors) }
    }
}

// Rescan result: bank SMS not in Kharcha (including deleted ones). Tick the ones to add.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RescanSheet(found: List<SmsCandidate>, onDismiss: () -> Unit, onAdd: (List<SmsCandidate>) -> Unit) {
    val picked = remember { mutableStateListOf<SmsCandidate>() }
    val allPicked = picked.size == found.size

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Panel
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = 24.dp)) {
            Text("Add from SMS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextMain)
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${found.size} bank SMS aren't in Kharcha. Tick the ones to add.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    picked.clear()
                    if (!allPicked) picked.addAll(found)
                }) { Text(if (allPicked) "Clear" else "Select all", color = Lime) }
            }
            Spacer(Modifier.height(6.dp))
            LazyColumn(
                modifier = Modifier.heightIn(max = 440.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(found, key = { "${it.timestamp}-${it.amount}" }) { c ->
                    val on = c in picked
                    CandidateRow(c, on) { if (on) picked.remove(c) else picked.add(c) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onAdd(picked.toList()) },
                enabled = picked.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    if (picked.isEmpty()) "Tick the spends to add"
                    else "Add ${picked.size} · ${money(picked.sumOf { it.amount })}",
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CandidateRow(c: SmsCandidate, checked: Boolean, onToggle: () -> Unit) {
    val whenText = remember(c.timestamp) {
        SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(c.timestamp))
    }
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(if (checked) Lime.copy(alpha = 0.10f) else Panel2)
            .clickable(onClick = onToggle)
            .padding(end = 12.dp, top = 4.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(checkedColor = Lime, checkmarkColor = Ink, uncheckedColor = Muted)
        )
        Column(Modifier.weight(1f).padding(top = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(money(c.amount), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextMain)
                Spacer(Modifier.width(8.dp))
                Text(whenText, style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            if (c.maybeLogged) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "Looks already added",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Coral,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp))
                        .background(Coral.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(c.preview, style = MaterialTheme.typography.bodySmall, color = Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}

// "160" for 160.0, "99.5" for 99.5 — how an amount looks in the edit box.
private fun plainAmount(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else v.toBigDecimal().stripTrailingZeros().toPlainString()

@Composable
fun EditDialog(e: Expense, onDismiss: () -> Unit, onSave: (Expense) -> Unit, onDelete: () -> Unit) {
    var amount by remember(e.id) { mutableStateOf(plainAmount(e.amount)) }
    var extra by remember(e.id) { mutableStateOf(if (e.extra > 0) plainAmount(e.extra) else "") }
    var note by remember(e.id) { mutableStateOf(e.note.orEmpty()) }
    var category by remember(e.id) { mutableStateOf(e.category) }

    val paid = amount.toDoubleOrNull()
    val extraValue = if (extra.isBlank()) 0.0 else extra.toDoubleOrNull()
    val valid = paid != null && paid > 0 && extraValue != null
    val whenText = remember(e.timestamp) {
        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(e.timestamp))
    }
    val origin = if (e.source == "manual") "Added by you" else "via ${e.source}"

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Panel,
        title = { Text("Edit spend", fontWeight = FontWeight.Bold, color = TextMain) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    listOfNotNull(e.merchant, origin, whenText).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = Muted
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    if (e.source == "manual") "Amount" else "Paid via ${e.source}",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMain
                )
                Spacer(Modifier.height(6.dp))
                AmountField(amount) { amount = it }
                Spacer(Modifier.height(10.dp))

                // Money added on top: ₹160 by GPay + ₹100 cash → ₹260 total, split kept.
                OutlinedTextField(
                    value = extra,
                    onValueChange = { s -> if (s.isEmpty() || s.matches(Regex("""\d*\.?\d{0,2}"""))) extra = s },
                    label = { Text("Extra on top (cash, tip…)") },
                    prefix = { Text("+ ₹ ", color = Lime) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = kharchaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (valid && extraValue!! > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Total ${money(paid!! + extraValue)}  (${money(paid)} + ${money(extraValue)})",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Lime
                    )
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Note (optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = kharchaFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))
                Text("Category", style = MaterialTheme.typography.labelLarge, color = TextMain)
                Spacer(Modifier.height(8.dp))
                CategoryChips(category) { category = it }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (valid) {
                        onSave(
                            e.copy(
                                amount = paid!!, extra = extraValue!!,
                                note = note.ifBlank { null }, category = category
                            )
                        )
                    }
                },
                enabled = valid
            ) {
                Text("Save", color = if (valid) Lime else Muted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("Delete", color = Coral) }
        }
    )
}
