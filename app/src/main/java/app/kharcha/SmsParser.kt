package app.kharcha

// Pure logic: given an SMS or notification text, decide if it's a spend and pull out the amount.
// Bank-agnostic — works for AU ("Dr INR 160.00"), cards ("spent Rs 250"), UPI ("paid Rs.250 to X").
data class ParsedSpend(val amount: Double, val merchant: String?, val category: String)

object SmsParser {

    // Money coming IN — ignore.
    private val creditWords = listOf(
        "credited", "credit of", "received", "reversed", "refund",
        "deposit", "has been credited", "cr inr", "cr rs"
    )

    // Money going OUT.
    private val debitWords = listOf(
        "dr inr", "dr rs", "debited", "debit of", "spent",
        "withdrawn", "paid", "purchase", "sent to", "txn of"
    )

    // First "INR 160.00" / "Rs.250" / "₹250" style amount.
    private val amountRegex = Regex("""(?i)(?:inr|rs|₹)\.?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")

    // Best-effort merchant: "to Zomato", "at BigBazaar". Often null for short bank SMS.
    private val merchantRegex = Regex("""(?i)(?:to|at)\s+([A-Za-z][A-Za-z0-9 &._@-]{2,30})""")

    fun parse(body: String): ParsedSpend? {
        val lower = body.lowercase()

        val isDebit = debitWords.any { lower.contains(it) }
        val isCredit = creditWords.any { lower.contains(it) }
        if (!isDebit) return null
        if (isCredit && !lower.contains("dr inr") && !lower.contains("dr rs")) return null

        val match = amountRegex.find(body) ?: return null
        val amount = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        if (amount <= 0) return null

        val merchant = merchantRegex.find(body)?.groupValues?.get(1)?.trim()?.ifBlank { null }
        return ParsedSpend(amount, merchant, Categorizer.guess(body))
    }
}

// Guesses a category from shop / brand names in the text. Add your local shops here.
object Categorizer {

    private val rules = listOf(
        "Food" to listOf(
            "zomato", "swiggy", "restaurant", "cafe", "pizza", "domino", "kfc", "mcdonald",
            "burger", "bakery", "starbucks", "chai", "biryani", "eatsure"
        ),
        "Travel" to listOf(
            "uber", "ola ", "olacabs", "rapido", "irctc", "redbus", "metro", "petrol", "fuel",
            "indigo", "makemytrip", "fastag", "parking", "airline", "hpcl", "bpcl", "iocl"
        ),
        "Bills" to listOf(
            "airtel", "jio", "vodafone", "bsnl", "electricity", "recharge", "broadband",
            "gas", "water bill", "insurance", "emi", "rent", "bescom", "tneb", "act fibernet"
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "croma", "decathlon",
            "reliance digital", "tata cliq"
        ),
        "Groceries" to listOf(
            "blinkit", "zepto", "bigbasket", "instamart", "dmart", "jiomart", "grocery",
            "supermarket", "kirana", "more retail"
        )
    )

    fun guess(text: String?): String {
        val t = text?.lowercase() ?: return "Other"
        return rules.firstOrNull { (_, words) -> words.any { t.contains(it) } }?.first ?: "Other"
    }
}
