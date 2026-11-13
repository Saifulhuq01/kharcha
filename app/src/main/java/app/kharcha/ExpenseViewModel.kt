package app.kharcha

import android.app.Application
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ExpenseViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).expenseDao()

    val expenses: StateFlow<List<Expense>> =
        dao.getAllExpenses().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun addExpense(amount: Double, category: String, note: String?) {
        viewModelScope.launch { dao.insert(Expense(amount = amount, category = category, note = note)) }
    }

    fun updateExpense(expense: Expense) {
        viewModelScope.launch { dao.update(expense) }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch { dao.delete(expense) }
    }

    fun scanSms(onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(SmsScanner.scanInbox(getApplication())) }
    }

    fun exportCsv(onReady: (Intent) -> Unit) {
        viewModelScope.launch { onReady(ExpenseExporter.exportCsv(getApplication())) }
    }
}
