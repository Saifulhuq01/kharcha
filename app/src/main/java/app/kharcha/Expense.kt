package app.kharcha

import androidx.room.Entity
import androidx.room.PrimaryKey

// One row = one spend.
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val merchant: String? = null,   // e.g. "Zomato" (null when typed by hand)
    val source: String = "manual",  // "manual", "SMS", "GPay", "PhonePe", ...
    val category: String = "Other", // one of CATS in Theme.kt
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
