# Manhaya v0.1

Android native starter project built with Kotlin + Jetpack Compose.

## Included
- Arabic-first / English UI with RTL layout
- Dark premium theme, five bottom tabs
- Sample home/discovery/detail/reader screens
- Local in-memory favorites (reset on app restart)
- GitHub Actions debug APK build

**Important:** This is an offline UI prototype. It does NOT fetch real manhwa, chapters, or images, and downloads are not implemented yet. No content provider or scraping integration is included.

## Build on GitHub
Upload all contents of this folder to repository root, commit to `main`, open **Actions** > **Build Manhaya APK**, then download the APK from the workflow's Artifacts after a successful build.

## Text localization
Edit `app/src/main/res/values/strings.xml` (English) and `app/src/main/res/values-ar/strings.xml` (Arabic).
