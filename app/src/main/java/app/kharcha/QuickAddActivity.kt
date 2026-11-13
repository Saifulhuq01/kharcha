package app.kharcha

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// The pop-up shown after you leave GPay/PhonePe when nothing was auto-logged.
class QuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val source = intent.getStringExtra("source") ?: "GPay"
        setContent {
            KharchaTheme {
                QuickAddCard(
                    source = source,
                    onSave = { amount, category -> save(amount, category, source); finish() },
                    onSkip = { finish() }
                )
            }
        }
    }

    private fun save(amount: Double, category: String, source: String) {
        val dao = AppDatabase.get(applicationContext).expenseDao()
        lifecycleScope.launch(Dispatchers.IO) {
            dao.insert(Expense(amount = amount, category = category, source = source))
        }
    }
}

@Composable
fun QuickAddCard(source: String, onSave: (Double, String) -> Unit, onSkip: () -> Unit) {
    var amount by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Food") }
    val value = amount.toDoubleOrNull()

    Surface(shape = RoundedCornerShape(28.dp), color = Panel) {
        Column(Modifier.fillMaxWidth().padding(24.dp)) {
            Text("You just used $source", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextMain)
            Text("How much did you spend?", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Spacer(Modifier.height(18.dp))
            AmountField(amount) { amount = it }
            Spacer(Modifier.height(14.dp))
            CategoryChips(category) { category = it }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onSkip) { Text("Skip", color = Muted) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { if (value != null && value > 0) onSave(value, category) },
                    enabled = value != null && value > 0,
                    colors = ButtonDefaults.buttonColors(containerColor = Lime, contentColor = Ink),
                    shape = RoundedCornerShape(14.dp)
                ) { Text("Save", fontWeight = FontWeight.Bold) }
            }
        }
    }
}
