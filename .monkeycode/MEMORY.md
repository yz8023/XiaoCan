# User Instruction Memory

This file records user instructions, preferences, and teachings for reference in future interactions.

## Format

### User Instruction Entry
[User Instruction Summary]
- Date: [YYYY-MM-DD]
- Context: [Mentioned scenario or time]
- Instructions:
  - [Content of user teaching or instruction, described line by line]

### Project Knowledge Entry
[Project Knowledge Summary]
- Date: [YYYY-MM-DD]
- Context: Discovered by Agent while performing [specific task description]
- Category: [Operations & Deployment|Build Methods|Testing Methods|Troubleshooting & Debugging|Workflow & Collaboration|Environment Configuration]
- Instructions:
  - [Specific knowledge points, described line by line]

## Deduplication Strategy
- Before adding a new entry, check for similar or identical instructions.
- If a duplicate is found, skip the new entry or merge it with the existing one.
- When merging, update the context or date information.
- This helps avoid redundant entries and keeps the memory file tidy.

## Entries

[Project Knowledge Summary]
- Date: 2026-09-30
- Context: Discovered by Agent while adding diagnostics logging and producing a release APK
- Category: Environment Configuration
- Instructions:
  - The current sandbox has no JDK, Gradle, or Android SDK installed and only ~200 MB free RAM, so the APK cannot be built locally.
  - Build the APK through the GitHub Actions workflow at `.github/workflows/android-release.yml` instead. Pushing to `main` triggers it; it publishes a GitHub Release tagged `build-<run_number>` with asset `XiaoCanPurify-<version>-release.apk`.
  - The `android-actions/setup-android@v3` action fails on current runners (`sdkmanager tools` package removed). Use the runner's preinstalled SDK at `/usr/local/lib/android/sdk` and call `sdkmanager` from `cmdline-tools/latest/bin`.
  - Toolchain versions: JDK 17, Gradle 9.5.1, AGP 9.2.1, compileSdk 37.

[Project Knowledge Summary]
- Date: 2026-09-30
- Context: Discovered by Agent while diagnosing a FATAL EXCEPTION on the OkHttp Dispatcher thread
- Category: Troubleshooting & Debugging
- Instructions:
  - Target app `com.realtech.xiaocan` may use ShellApplication / Aliyun Jiagu packing; hook `Application`/`Activity` lifecycle when target classes are not loadable at `onPackageReady`.
  - The module logs are disabled by default. Users enable them from the module app "运行日志" switch, which writes the framework remote preference group `xiaocanpurify` key `enable_log` (module app writes via `XposedServiceHelper`; target reads via `XposedInterface.getRemotePreferences`). Restart the target app after toggling.
  - When enabled, runtime logs and uncaught crash stack traces go to `Android/data/com.realtech.xiaocan/files/XiaoCanPurify.log` (also best-effort `Download/XiaoCanPurify.log`). Ask the user to send this file after a crash, since logcat is not always accessible.
  - OkHttp's `AsyncCall.run()` rethrows non-`IOException` throwables from the dispatcher thread; module interceptors must convert any failure to a passthrough or `IOException` to avoid crashing the target app.

[Project Knowledge Summary]
- Date: 2026-10-12
- Context: Established while restructuring the repo as an upstream feature stack and merging upstream release 1.2
- Category: Workflow & Collaboration
- Instructions:
  - Repo layout: `main` = upstream `Jessire/XiaoCanPurify` history + local feature commits (crash-safe NetworkAdInterceptor, diagnostics logging, CI workflow, version/docs). Upstream remote is `upstream`; upstream-owned files (WithdrawAdPolicy, PopupBlocker, tests, CHANGELOG) must stay identical to upstream.
  - Sync procedure when upstream releases: run `bash sync-upstream.sh` (fetches upstream and rebases the feature stack), then `git push --force-with-lease origin main`.
  - Recurring rebase conflict point: `NetworkAdInterceptor.java`. Resolution: keep the local crash-safe `invoke()`/`matchMockResponse()` structure and merge upstream's new keyword entries into `isWithdrawCardName` / `isAdOrTrackingUrl`.
  - Local-only files that must survive every sync: `ModuleLog.java`, MainActivity log toggle, `MainHook` wiring (`installContextCapture`), `AndroidManifest.xml` XposedProvider, `.github/workflows/android-release.yml`, `gradle.properties` sdkDownload flag.
  - The old pre-stack commit `faafabe` (1.1-based snapshot) remains reachable via `origin/main` history and as reference for restoring any missed file: `git checkout faafabe -- <path>`.
