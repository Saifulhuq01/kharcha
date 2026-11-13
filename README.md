# Kharcha

**The expense tracker that fills itself in.**

Kharcha (खर्चा, "expense") is an offline, privacy-first Android app that logs your
spends automatically: from bank debit SMS, from GPay / PhonePe / Paytm / BHIM
notifications, and, when neither catches it, with a quick *"How much did you spend?"*
pop-up the moment you leave a payment app.

No account. No cloud. No ads. Your data never leaves your phone.

> Built for India 🇮🇳, where almost every rupee you spend leaves a trace in an SMS or a UPI
> notification. Kharcha turns those traces into a spending log you don't have to type.

---

## Features

- **Auto-capture from bank SMS.** Reads debit alerts (`Dr INR 160.00…`, `debited`, `spent`),
  skips credits, and works across banks without per-bank setup.
- **Auto-capture from payment apps.** Catches GPay, PhonePe, Paytm and BHIM payment
  notifications, often with the merchant name.
- **"How much?" pop-up.** Leave a payment app without the spend being caught and Kharcha
  asks you, with the amount box ready.
- **No double counting.** A payment seen as both an SMS and a notification is logged once.
- **Auto-categorise.** Zomato → Food, Uber → Travel, Blinkit → Groceries, and so on.
  Tap any spend to change its category.
- **Monthly view.** This month's total, a split bar by category, and spends grouped by day.
- **Back-fills your history.** On first run it imports the debit SMS already in your inbox.
- **CSV export.** Share your whole log as a spreadsheet.
- **Survives reboots.** The watcher restarts by itself after a restart or an update.

## How it works

| Source | What triggers it | Android feature |
|---|---|---|
| Bank SMS | A debit alert arrives | `SMS_RECEIVED` broadcast + inbox scan |
| Payment apps | GPay / PhonePe / Paytm / BHIM posts a "paid" notification | `NotificationListenerService` |
| Exit pop-up | You leave a payment app and nothing was logged within ~30 s | `UsageStatsManager` + foreground service |

Every source goes through the same parser ([`SmsParser.kt`](app/src/main/java/app/kharcha/SmsParser.kt)),
which pulls out the amount, a best-guess merchant and a category, then writes to a local
Room (SQLite) database.

## Privacy

Kharcha reads sensitive things (your SMS and notifications), so here is exactly what it does with them:

- Everything is processed **on the device**. The app has **no internet permission**, so it
  cannot send your data anywhere.
- It only stores the spend itself: amount, merchant guess, category, time and source. It
  does not keep the SMS text.
- Uninstalling the app deletes all its data.

## Install

Download `Kharcha.apk` from the [Releases](../../releases) page. **Requires Android 8.0 or newer.**

> **⚠️ In India and some other countries, Play Protect blocks this install.**
> Google's fraud protection automatically blocks any app installed from a browser, file manager
> or chat app if it asks to read SMS or notifications. Kharcha needs both to work.
> You'll see *"App blocked to protect your device"* with only an **OK** button; there is no
> "Install anyway". This is a permission rule, not a malware finding: the full source is right
> here for you to check. Use one of the two options below.

**Option A: install over USB (recommended, keeps Play Protect on)**

1. On the phone, turn on *Developer options* and then *USB debugging*.
2. Connect it to a computer that has [Android platform-tools](https://developer.android.com/tools/releases/platform-tools) and run:
   ```bash
   adb install Kharcha.apk
   ```

**Option B: turn Play Protect off just for the install**

1. Play Store → your profile icon → **Play Protect** → ⚙️ → turn off **Scan apps with Play Protect**.
2. Open `Kharcha.apk` and install it (allow *Install unknown apps* if asked).
3. **Turn Scan apps with Play Protect back on straight away.** While it's off your phone is less
   protected, so don't install anything else in between.

Where the block doesn't apply, step 2 of Option B on its own is enough.

**Then finish setup.** Open Kharcha and complete the **Turn on auto-capture** card:

- **Read bank SMS**: logs every debit alert
- **Payment notifications**: GPay, PhonePe, Paytm, BHIM
- **Usage access**: knows when you leave a payment app
- **Show over other apps**: for the "how much?" pop-up

The card disappears once all four are on, and the header shows **Auto-capture on**.

> **Why not on the Play Store?** Google only allows SMS permissions for default SMS apps,
> so an app like this has to be installed directly.

## Build from source

You need **JDK 17** and the **Android SDK** (platform 35, build-tools 35). Android Studio
includes both, or you can use the command-line tools.

```bash
git clone https://github.com/Saifulhuq01/kharcha.git
cd kharcha
echo "sdk.dir=$HOME/Android/Sdk" > local.properties   # path to your Android SDK
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Tech: Kotlin · Jetpack Compose (Material 3) · Room · Coroutines/Flow · KSP.

## Project structure

```
app/src/main/java/app/kharcha/
├── MainActivity.kt         Home screen (hero card, setup card, day-grouped list, add sheet)
├── QuickAddActivity.kt     The "how much did you spend?" pop-up
├── Theme.kt / Components.kt  Colours, categories, shared UI pieces
├── SmsParser.kt            Debit detection, amount + merchant extraction, Categorizer
├── SmsReceiver.kt          Live SMS capture
├── SmsScanner.kt           One-time inbox back-fill (dedup-safe)
├── NotifListenerService.kt Payment-app notification capture
├── WatcherService.kt       Detects leaving a payment app → pop-up
├── Setup.kt                Live permission checks, watcher auto-start, boot receiver
├── ExpenseExporter.kt      CSV export
└── AppDatabase.kt / ExpenseDao.kt / Expense.kt   Room storage
```

## Contributing

The easiest and most useful contributions are small:

- **Your bank's SMS isn't detected?** Open an issue with the SMS text (**mask your account
  number and balance**), or add its wording to `debitWords` / `creditWords` in `SmsParser.kt`.
- **A shop lands in the wrong category?** Add its name to the lists in `Categorizer`.
- **Another UPI app?** Add its package name to `PAYMENT_APPS` in `NotifListenerService.kt`.

Pull requests are welcome. Please keep the app offline and free of trackers.

## Roadmap

- [ ] Monthly budgets with a warning when you're close
- [ ] Month-by-month history and trends
- [ ] Encrypted backup / restore to a file
- [ ] Custom categories
- [ ] Home-screen widget
- [ ] Split a spend with friends

## License

[MIT](LICENSE)
