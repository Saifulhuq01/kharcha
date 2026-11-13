package app.kharcha

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

// One row = one spend.
@Entity(tableName = "expenses")
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,             // what was paid / what the SMS said (SMS rows keep this as-is)
    val merchant: String? = null,   // e.g. "Zomato" (null when typed by hand)
    val source: String = "manual",  // "manual", "SMS", "GPay", "PhonePe", ...
    val category: String = "Other", // one of CATS in Theme.kt
    val note: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    @ColumnInfo(defaultValue = "0")
    val extra: Double = 0.0         // added on top, e.g. ₹100 cash with a ₹160 GPay payment
)

// What the spend really cost: the paid amount plus any extra.
val Expense.total: Double get() = amount + extra
