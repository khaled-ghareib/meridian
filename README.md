# ◆ Meridian — Personal Finance

A private, offline money tracker in EGP: income, expenses, savings, budgets, charts and
plain-language insights. Your data stays on your phone.

- **Android:** a real app (APK), built automatically by GitHub.
- **iPhone:** a home-screen app, published automatically to GitHub Pages.

---

## One-time setup

1. Create a free GitHub account and a **public** repository (for example `meridian`).
2. Upload everything in this folder to the repository (keep the folder structure,
   including the hidden `.github` folder).
3. In the repository go to **Settings › Pages** and set **Source** to **GitHub Actions**
   (this is what publishes the iPhone version).
4. Open the **Actions** tab. A build named *Build Meridian* starts on its own
   (or press **Run workflow**). It takes about 3–5 minutes.

## Android — install the APK

1. On your phone, open the repository's **Releases** page (right side of the main page).
2. Download **Meridian.apk** from the newest release.
3. Open the file. Android will ask to allow installs from your browser or Files app —
   allow it once, then tap **Install**.

**Updates:** every change to the project produces a new release. Install it on top of the
old app — your data is kept (every build is signed with the same private key).

## iPhone — add to Home Screen

1. Open `https://YOUR-USERNAME.github.io/meridian/` in **Safari**.
2. Tap **Share › Add to Home Screen**.
3. Open Meridian from the new icon. After the first open it also works offline.

## Your data

- Everything is stored only on the phone you use. Android and iPhone copies don't sync.
- **Settings › Back up data** saves a backup file (save it to Google Drive, Files, WhatsApp…).
- **Settings › Restore from backup** brings it back — also how you move data between phones.
- **Settings › Export to spreadsheet** gives a CSV that opens in Excel.
- Uninstalling the Android app deletes its data, so back up first.

## Project layout

```
www/                  the app itself (one HTML file, fonts, icons, offline support)
android/              Android wrapper that runs www/ fully offline
.github/workflows/    the automatic build (APK + iPhone version)
```

Note: the app's signing key is kept in the repository's private **Actions secrets**
(`MERIDIAN_KEYSTORE_B64`, `MERIDIAN_KEYSTORE_PASSWORD`), not in these files. Keep a copy
of it somewhere safe — without the same key, a new APK can't update the installed app.
