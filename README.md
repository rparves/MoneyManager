# Money Manager

A small, offline personal money manager for Android (8.0+), built with Kotlin and Jetpack Compose.

**Download the latest APK:** [MoneyManager.apk](../../releases/latest/download/MoneyManager.apk)

## Features
- Record expenses and income with category, account, date and note
- Home dashboard: total balance, this month's income and expenses, next bill, recent activity
- Records: month-by-month daily list with day totals and search
- Reports: week / month / year summaries, spending-by-category donut, 6-month income vs expense bars
- Planner: savings goals with progress, recurring bills with "mark paid" (optionally records the expense)
- Daily reminder at 11:00 PM (changeable) to record the day's expenses; mentions bills due tomorrow
- Light and dark theme, currency choice (৳ default), all data stored on the device

## Build
GitHub Actions builds a minified, resource-shrunk release APK on every push to `main` and publishes it as a release.
For a stable signing key, add the repository secrets `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD`.
