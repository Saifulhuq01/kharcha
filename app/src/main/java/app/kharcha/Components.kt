package app.kharcha

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.util.Locale

// "₹1,23,456.5" — Indian digit grouping.
private val inr = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
    maximumFractionDigits = 2
    minimumFractionDigits = 0
}

fun money(v: Double): String = "₹" + inr.format(v)

// Horizontally scrolling emoji chips for picking a category.
@Composable
fun CategoryChips(selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CATS.forEach { c ->
            val on = c.name == selected
            Surface(
                onClick = { onSelect(c.name) },
                shape = RoundedCornerShape(14.dp),
                color = if (on) c.color.copy(alpha = 0.22f) else Panel2,
                border = if (on) BorderStroke(1.dp, c.color) else null
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(c.emoji, fontSize = 16.sp)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        c.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = if (on) TextMain else Muted
                    )
                }
            }
        }
    }
}

@Composable
fun kharchaFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Lime,
    unfocusedBorderColor = Panel2,
    focusedLabelColor = Lime,
    cursorColor = Lime,
    focusedContainerColor = Panel2,
    unfocusedContainerColor = Panel2
)

// Big ₹ amount input. Only accepts numbers with up to 2 decimals.
@Composable
fun AmountField(value: String, onChange: (String) -> Unit) {
    val big = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold)
    OutlinedTextField(
        value = value,
        onValueChange = { s -> if (s.isEmpty() || s.matches(Regex("""\d*\.?\d{0,2}"""))) onChange(s) },
        placeholder = { Text("0", style = big, color = Muted) },
        prefix = { Text("₹ ", style = big, color = Lime) },
        textStyle = big.copy(color = TextMain),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        shape = RoundedCornerShape(18.dp),
        colors = kharchaFieldColors(),
        modifier = Modifier.fillMaxWidth()
    )
}
