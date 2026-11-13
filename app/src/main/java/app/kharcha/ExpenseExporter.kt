package app.kharcha

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Writes all expenses to a CSV file and returns a share Intent.
object ExpenseExporter {

    suspend fun exportCsv(context: Context): Intent = withContext(Dispatchers.IO) {
        val rows = AppDatabase.get(context).expenseDao().getAllOnce()
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        // Amount = what the spend really cost; Paid + Extra show how it was made up.
        val sb = StringBuilder("Date,Amount,Paid,Extra,Category,Source,Merchant,Note\n")
        for (e in rows) {
            sb.append(fmt.format(Date(e.timestamp))).append(',')
                .append(e.total).append(',')
                .append(e.amount).append(',')
                .append(e.extra).append(',')
                .append(csv(e.category)).append(',')
                .append(csv(e.source)).append(',')
                .append(csv(e.merchant ?: "")).append(',')
                .append(csv(e.note ?: "")).append('\n')
        }

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "kharcha-expenses.csv")
        file.writeText(sb.toString())

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    // Quote a field if it has a comma or quote, so the CSV stays valid.
    private fun csv(value: String): String =
        if (value.contains(',') || value.contains('"')) "\"" + value.replace("\"", "\"\"") + "\""
        else value
}
