package app.kharcha

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Kharcha's look: near-black ink, lime accent.
val Ink = Color(0xFF0E1013)
val Panel = Color(0xFF171A20)
val Panel2 = Color(0xFF232832)
val Lime = Color(0xFFC6F432)
val Coral = Color(0xFFFF6B6B)
val Muted = Color(0xFF8B93A1)
val TextMain = Color(0xFFF2F4F7)

private val KharchaColors = darkColorScheme(
    primary = Lime, onPrimary = Ink,
    secondary = Coral, onSecondary = Ink,
    background = Ink, onBackground = TextMain,
    surface = Panel, onSurface = TextMain,
    surfaceVariant = Panel2, onSurfaceVariant = Muted,
    surfaceContainerLowest = Ink, surfaceContainerLow = Panel, surfaceContainer = Panel,
    surfaceContainerHigh = Panel2, surfaceContainerHighest = Panel2,
    outline = Color(0xFF3A404C), outlineVariant = Color(0xFF2A2F38),
    error = Coral
)

@Composable
fun KharchaTheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = KharchaColors, content = content)

// Spending categories: name, emoji and the colour used in the bar and icons.
data class Cat(val name: String, val emoji: String, val color: Color)

val CATS = listOf(
    Cat("Food", "🍔", Color(0xFFFF8A65)),
    Cat("Travel", "✈️", Color(0xFF4FC3F7)),
    Cat("Bills", "💡", Color(0xFFFFD54F)),
    Cat("Shopping", "🛍️", Color(0xFFBA68C8)),
    Cat("Groceries", "🛒", Color(0xFF81C784)),
    Cat("Other", "📦", Color(0xFF90A4AE))
)

fun cat(name: String) = CATS.firstOrNull { it.name == name } ?: CATS.last()
