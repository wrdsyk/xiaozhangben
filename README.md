# 记账

A personal bookkeeping app with a liquid-glass look. It has seven themes: light 珍珠 (pearl), 冰川 (glacier), 抹茶 (matcha) and 樱粉 (sakura), and dark 暮蓝 (dusk), 深海 (abyss) and 墨夜 (ink, near-black for OLED). Picking one in 我的 reveals it as a circle growing from the tapped card. Data is stored only on the phone; the app goes online only to check GitHub for a newer release.

## Features (v0.4)
- **记一笔 (add an entry)**: pick a category, type the amount and tap 完成. The keypad does addition and subtraction. You can also set the date and a note. 再记 saves the entry and keeps the sheet open for the next one.
  - **Categories**: the dashed 添加 tile creates one (name up to 6 characters, a colour, and an icon from about 100 built-in icons). Long-press any category to enter edit mode: every tile gets a pencil badge, drag tiles to reorder, and tap one to change its name, icon or colour (built-ins too; they can be reset to the original). The last colour swatch opens a palette with RGB sliders and a hex field. Deleting a custom category moves its records to 其他.
  - **Note chips**: a note used 3 or more times in a category shows as a chip under the note field. Tapping one fills it in and puts the cursor at the end. Long-press for × to hide one, and use the + at the end of the row to add your own.
  - **Photos**: the image button at the right of the note field takes a photo or picks from the gallery (up to 9 per record). Tap a photo to view it full screen, with pinch zoom. Photos also show on the detail page.
  - **Date**: three wheels for 年 / 月 / 日, plus 今天 / 昨天 / 前天 / 一周前 shortcuts.
- **明细 (records)**: grouped by day. The magnifier at the top right searches every month by note, category or amount.
  - Swipe left to delete; deletes can be undone.
  - Tap an entry to expand it into the detail page, where you can edit or delete it.
- **统计 (stats)**:
  - A 年 / 月 / 周 switch at the top. The card under it totals the period and names it: 今年支出, 本月支出, 本周支出 (then 去年, 上月, 上周, or 2024年 / 8月 / 第44周 for older ones). Tap that title for a dropdown of the periods that have records (the current one is always listed), newest first. Weeks start on Monday and are numbered the ISO-8601 way (week 1 holds the year's first Thursday), and each shows its date range, e.g. 10月26日–11月1日.
  - The average follows the switch: 月均 for a year (÷ the months so far this year, ÷ 12 for past years), 日均 for a month or a week (÷ the days so far, or the whole month / 7 days once it is over).
  - A spending chart for that period: months 1–12月, the days of the month, or 周一 to 周日. Press and drag on it for a popup with the point's 3 largest expenses and its total.
  - A category donut chart for the period.
  - 排行榜 (ranking): the 10 largest single expenses of the period; tap one to open it.
- **预算 (budget tab)**:
  - The total budget card at the top swipes between 年 / 月 / 日. The monthly one drives the ring on the home page.
  - **自定义预算 (custom budgets)**: daily, monthly or yearly caps, on all spending or on one category. They're grouped by period, sortable by 可用 or 已用, and their bars turn green, yellow, then red as they fill.
  - **Amount chips**: any budget amount you save once becomes a chip under the amount field (per 年 / 月 / 日). Long-press for × to remove one, + to add your own.
  - Tapping 预算已用 or 预算还剩 on the 明细 card opens this tab.
- **定时记账 (recurring records)**: 我的 → 定时记账. Rules repeat 每天, 工作日 / 休息日 (follow the statutory holidays and 调休 workdays of Mainland China, built in for 2025 and 2026; other years fall back to Mon–Fri / weekends), chosen weekdays, several days of the month (29–31 fall on the last day of short months, and picks that land on the same date write one record) or several dates of the year (2月29日 runs on 28 Feb in other years). Due records are written when the app opens, each dated on its own day at 09:00; missed days are caught up. Paused days are skipped for good, and deleting a rule keeps the records it wrote.
- **备份 (backup)**: 我的 → 备份与恢复 exports and imports JSON files. Backups include records with their photos, budgets, categories (with edits and order), note and amount chips, and recurring rules. Older backup files still import.
- **从鲨鱼记账导入 (import from 鲨鱼记账)**: 我的 → 备份与恢复 → 从鲨鱼记账导入 reads the CSV that 鲨鱼记账 exports (GBK/GB18030 or UTF-8; columns are found by name, dates like `2023年12月31日`). A preview shows the totals, the date range, rows already in the ledger (same day, category, amount and note; these are skipped, so importing the same file twice adds nothing) and lines that couldn't be read. Categories are matched by name, including ones you renamed; names the ledger doesn't have are created as new categories with a fitting icon, or you can send those rows to 其他. Rows have no time of day, so each day's rows are placed at noon in the file's order. It adds to your records and never replaces them.
- **Quick edit**: in the record list, tapping the icon or the amount opens the edit sheet directly. Tapping the middle of the row opens the detail page.

## Updates
The app updates itself from GitHub Releases. This is the only thing it uses the network for, and nothing from the ledger is ever sent.

**On the phone**
- 我的 → 检查更新 checks right away. The right side shows the current version, or 有新版本 vX when one is waiting. The result is a toast (已是最新版本, or the error) or the update sheet.
- 我的 → 自动检查更新 (on by default): when the app opens, it checks at most once every 24 hours. A newer version opens the update sheet; 跳过这个版本 silences that version for the automatic check (a manual check still shows it). With the switch off, or offline, it never asks.
- The update sheet shows the release notes (the markdown stripped to plain text), the APK size and 立即更新. It then downloads with a progress bar (取消 stops it; closing the sheet does not), checks the file and hands it to the system installer.
- Before installing, the app refuses any APK that is not this app (`com.jizhang`), does not have a higher versionCode, or is signed with a different certificate (compared by SHA-256). Android would reject a mismatched signature anyway, but only after the confirmation, and installing over it by uninstalling first would delete the ledger.
- The first time, Android asks you to allow 记账 to install apps (允许安装未知应用); the update carries on when you come back. On Android 12+ later updates can install without the confirmation, once 记账 is the installer of record. After the install, the app closes; open it again for the new version.
- Offline, rate-limited, or a repo with no releases yet (→ 还没有发布版本) just give a message. An automatic check that fails says nothing.

**Where it looks**: `jizhang.updateRepo=owner/repo` in `gradle.properties` (now `wrdsyk/xiaozhangben`, exposed as `BuildConfig.UPDATE_REPO`). Blank means no update source: 我的 shows 未配置更新源 and the app never goes online. The repo has to be public, because the app does not log in.

**Publishing a release** (GitHub CLI, logged in with `gh auth login`):
1. Raise `versionCode` and `versionName` in `app/build.gradle.kts`. The tag is `v<versionName>`, and the new `versionCode` has to be higher than the installed one.
2. Build with the same machine's key as the installed app: `JAVA_HOME=/f/AndroidStudio/jbr ./gradlew :app:assembleRelease`.
3. Copy `app/build/outputs/apk/release/app-release.apk` to `jizhang-<versionName>.apk`. Upload exactly one `.apk` asset; the app takes the first one.
4. Write the notes in Chinese markdown in a file (`notes.md`), then:
   ```bash
   gh release create v0.5.1 jizhang-0.5.1.apk --repo wrdsyk/xiaozhangben --title "记账 v0.5.1" --notes-file notes.md
   ```
   Don't mark it a draft or pre-release: the app only sees the repo's latest full release.

## Build and install
Release APK (minified, no sample data, signed with this machine's debug key):
```bash
JAVA_HOME=/f/AndroidStudio/jbr ./gradlew :app:assembleRelease
```
The APK is written to `app/build/outputs/apk/release/app-release.apk`, and a copy is kept in `dist/`.

Future updates must be built on this same computer (or with a copy of `~/.android/debug.keystore`). Otherwise Android rejects the update, and installing it means uninstalling first, which deletes the data. Export a backup before switching machines.

For a debug build installed over USB:
```bash
JAVA_HOME=/f/AndroidStudio/jbr ./gradlew :app:installDebug
```

Debug builds fill in three months of sample data on first launch. To clear it, go to 我的 → 清除示例数据.

## Testing on the phone
1. On the phone, turn on Developer options → USB debugging.
2. Connect it to the computer and accept the authorization prompt.
3. Check that the phone shows up:
   ```bash
   /f/AndroidSDK/platform-tools/adb.exe devices
   ```
4. Run the install command above.
