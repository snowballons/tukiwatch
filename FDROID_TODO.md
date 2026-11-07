# TukiWatch: Step-by-Step F-Droid Inclusion Checklist & Roadmap

> **Target Package:** `com.tukiwatch.app`  
> **Source Repository:** `https://github.com/snowballons/tukiwatch`  
> **License:** MIT (`LICENSE` at root)  
> **Submission Channel:** Merge Request (MR) to `fdroid/fdroiddata` on GitLab  
> **Signing Strategy:** Option B (Reproducible Build with Upstream Signing Key)  
> **Signing Certificate SHA-256:** `4f081d95c94616b70f78e2fa7e60e5a81780bcc7e99998c3ff19d9641dbd8fd5`  
> **Guidance Basis:** F-Droid 2026 Inclusions Standards, `fdriod1.md`, and technical corrections from `fdriod1-review.md`.

---

## Roles & Responsibility Matrix

| Legend | Owner | Scope & Responsibility |
| :--- | :--- | :--- |
| 🤖 **Antigravity (Agent)** | AI Coding Assistant | Codebase audits, Gradle verification, Fastlane metadata scaffolding, text limit enforcement, recipe YAML drafting, and local linting commands. |
| 👤 **Jonnie (User)** | Project Owner / Developer | Architectural decisions, release keystore management, GitLab account actions, pushing release tags, creating GitHub releases, opening the MR on GitLab, and communicating with F-Droid maintainers. |

---

## Master Status Tracker

- [x] **Phase 1: Compliance, Licensing & Dependency Audit** *(Complete)*
- [x] **Phase 2: Signing Strategy Decision (Reproducible Build Selected)** *(Complete)*
- [x] **Phase 3: Fastlane Store Metadata Preparation** *(Complete)*
- [ ] **Phase 4: Release Cut & Commit Hash Capture (Crucial Sequencing)**
- [x] **Phase 5: F-Droid Build Recipe Authoring (`metadata/com.tukiwatch.app.yml`)** *(Complete)*
- [ ] **Phase 6: Local & CI Pre-Flight Verification**
- [ ] **Phase 7: GitLab Fork & Merge Request Submission**
- [ ] **Phase 8: Review Interaction, CI Monitoring & Acceptance**
- [ ] **Phase 9: Post-Merge Verification & Repository Maintenance**

---

## Phase 1: Compliance, Licensing & Dependency Audit

Ensure TukiWatch strictly complies with F-Droid's Inclusion Policy before authoring metadata.

### Tasks
- [x] 🤖 **Verify FOSS License File**: Ensure `LICENSE` (MIT) exists at repository root. *(Completed: MIT License present)*.
- [x] 🤖 **Audit Dependencies for Non-Free / Tracking SDKs**:
  - Scan `app/gradle/libs.versions.toml` to verify zero proprietary SDKs (no Google Play Services, Firebase, Crashlytics, proprietary analytics, or ad networks).
  - Verify embedded Python dependencies: `streamlink==8.6.2` (BSD-2-Clause) and `chaquopy==17.0.0` (MIT).
  - *Status:* Clean. All dependencies are Apache 2.0, MIT, or BSD.
- [x] 🤖 **Assess Anti-Features**:
  - TukiWatch has no user tracking, no ads, and no backend service.
  - *Evaluation Note:* TukiWatch streams content from external third-party platforms (Twitch, Kick, YouTube). If requested by maintainers, the `NonFreeNet` anti-feature flag can be added.
- [x] 🤖 **Verify Offline / Secret-Free Build**:
  - Confirmed `assembleRelease` builds cleanly from terminal without requiring secrets, API tokens, or production keystores (debug signing fallback already wired).

---

## Phase 2: Signing Strategy Decision (Reproducible vs. F-Droid Key)

*Context from Review:* Android prohibits installing an APK over an existing installation if signed with a different key. Switching later requires users to uninstall and lose local data.

### Tasks
- [x] 👤 **Decision: Choose Signing Strategy**:
  - **Selected:** **Option B (Reproducible Build - Recommended)**. F-Droid compiles from source, verifies that its output matches your GitHub Release APK bit-for-bit, and distributes your upstream binary signed with your key. Users can cross-update between GitHub and F-Droid.
- [x] 🤖 **Extract Certificate Fingerprint**:
  - Extracted from official release APK: `CN=Jonnie, OU=Development, O=TukiWatch, L=Nairobi, ST=Nairobi, C=KE`.
  - **SHA-256 (lowercase hex without colons):**
    ```
    4f081d95c94616b70f78e2fa7e60e5a81780bcc7e99998c3ff19d9641dbd8fd5
    ```

---

## Phase 3: Fastlane Store Metadata Preparation

*Crucial Rule from 2026 Review:* F-Droid pulls metadata from the **release commit** it builds. Fastlane metadata must be committed to the repository **before** the release tag is created.

### Required Directory Structure
```
fastlane/metadata/android/en-US/
├── title.txt
├── short_description.txt
├── full_description.txt
├── changelogs/
│   └── 15.txt (named after Android versionCode, not versionName!)
└── images/
    ├── icon.png (512x512)
    ├── featureGraphic.png (1024x500)
    └── phoneScreenshots/
        ├── 1.png
        ├── 2.png
        ├── 3.png
        └── 4.png
```

### Tasks
- [x] 🤖 **Create Fastlane Directory Tree**: Scaffolded `fastlane/metadata/android/en-US/`.
- [x] 🤖 **Draft `title.txt`**:
  - Limit: ≤ 50 characters.
  - Value: `TukiWatch` (9 characters).
- [x] 🤖 **Draft `short_description.txt`**:
  - Limit: ≤ 80 characters.
  - Strict Rule: **No trailing period**.
  - Value: `Privacy-first live stream player with an on-device Streamlink engine` (68 characters).
- [x] 🤖 **Draft `full_description.txt`**:
  - Limit: ≤ 4000 characters.
  - Value: 1,307 characters with clean HTML (`<p>`, `<b>`, `<ul>`, `<li>`).
- [x] 🤖 **Create `changelogs/<versionCode>.txt`**:
  - Created `changelogs/15.txt` matching `APP_VERSION_CODE = 15` (390 characters, ≤ 500 characters).
- [x] 🤖 **Format & Place Image Assets**:
  - Generated `icon.png` (512x512 PNG).
  - Generated `featureGraphic.png` (1024x500 PNG banner).
  - Placed 4 phone screenshots (720x1640 PNGs) into `phoneScreenshots/`.
- [ ] 👤 **Review & Approve Metadata**: Review text copy and screenshots before pushing.

---

## Phase 4: Release Cut & Commit Hash Capture

*Critical Technical Requirement:* F-Droid's current `fdroiddata` policy requires a **full 40-character Git commit hash** in the build recipe, rather than a tag name or branch.

### Tasks
- [ ] 🤖 **Verify Version Alignment**:
  - `app/gradle.properties`: `APP_VERSION_NAME=1.1.8`, `APP_VERSION_CODE=15`.
  - `version.json`: `1.1.8`, `versionCode: 15`.
- [ ] 🤖 **Commit Fastlane Metadata to Main**:
  ```bash
  git add fastlane/
  git commit -m "chore(metadata): add Fastlane store metadata for F-Droid"
  git push origin main
  ```
- [ ] 👤 **Tag Release Commit**:
  ```bash
  git tag -a v1.1.8 -m "Release v1.1.8"
  git push origin v1.1.8
  ```
- [ ] 🤖 **Capture Exact 40-Character Commit Hash**:
  ```bash
  git rev-parse v1.1.8
  ```
- [ ] 👤 **Publish GitHub Release**:
  - Ensure GitHub release exists matching tag `v1.1.8`.
  - Ensure the release APK `androidApp-release.apk` is attached to the release.

---

## Phase 5: F-Droid Build Recipe Authoring

Author `metadata/com.tukiwatch.app.yml` conforming to `fdroiddata` specifications.

### Tasks
- [x] 🤖 **Draft `metadata/com.tukiwatch.app.yml`**:
  Drafted at `fdroid/metadata/com.tukiwatch.app.yml`:
  ```yaml
  Categories:
    - Video
    - Internet
  License: MIT
  AuthorName: Snowballons
  SourceCode: https://github.com/snowballons/tukiwatch
  IssueTracker: https://github.com/snowballons/tukiwatch/issues
  Changelog: https://github.com/snowballons/tukiwatch/blob/main/CHANGELOG.md
  Donate: https://buymeacoffee.com/snowballons

  RepoType: git
  Repo: https://github.com/snowballons/tukiwatch.git

  Builds:
    - versionName: '1.1.8'
      versionCode: 15
      commit: <FULL_40_CHAR_COMMIT_HASH>
      subdir: app
      gradle:
        - yes

  AutoUpdateMode: Version
  UpdateCheckMode: Tags
  CurrentVersion: '1.1.8'
  CurrentVersionCode: 15

  Binaries: https://github.com/snowballons/tukiwatch/releases/download/v%v/androidApp-release.apk
  AllowedAPKSigningKeys: 4f081d95c94616b70f78e2fa7e60e5a81780bcc7e99998c3ff19d9641dbd8fd5
  ```
- [ ] 👤 **Review Draft Recipe**: Verify URLs and version codes.

---

## Phase 6: Local & CI Pre-Flight Verification

Ensure the build recipe passes F-Droid validation tooling before submitting the Merge Request.

### Tasks
- [x] 🤖 **Pre-Flight Sanity Checklist**:
  - [x] Application builds with `./gradlew :androidApp:assembleRelease` with no external dependencies required.
  - [x] No precompiled proprietary `.so`, `.jar`, or `.aar` files checked into git.
  - [x] Application ID in `build.gradle.kts` matches `com.tukiwatch.app`.
  - [x] Subdirectory `app` correctly specified.
- [ ] 🤖 / 👤 **Test Recipe with `fdroidserver` (Optional but Recommended)**:
  - If `fdroidserver` is installed:
    ```bash
    fdroid lint com.tukiwatch.app
    fdroid rewritemeta com.tukiwatch.app
    fdroid checkupdates --allow-dirty com.tukiwatch.app
    ```
  - Alternatively, push to your GitLab fork and let GitLab CI run the test suite.

---

## Phase 7: GitLab Fork & Merge Request Submission

Open the official Merge Request in the `fdroid/fdroiddata` repository.

### Tasks
- [ ] 👤 **Create / Log in to GitLab Account**: Navigate to [gitlab.com](https://gitlab.com).
- [ ] 👤 **Fork `fdroid/fdroiddata`**:
  - Fork `https://gitlab.com/fdroid/fdroiddata` to your personal GitLab namespace.
- [ ] 👤 / 🤖 **Clone Fork & Create Feature Branch**:
  ```bash
  git clone https://gitlab.com/<YOUR_GITLAB_USERNAME>/fdroiddata.git
  cd fdroiddata
  git checkout -b com.tukiwatch.app
  ```
- [ ] 🤖 **Place Recipe in Fork**:
  - Copy `fdroid/metadata/com.tukiwatch.app.yml` into `metadata/com.tukiwatch.app.yml` in the fork.
- [ ] 👤 / 🤖 **Commit & Push to GitLab**:
  ```bash
  git add metadata/com.tukiwatch.app.yml
  git commit -m "New App: com.tukiwatch.app"
  git push -u origin com.tukiwatch.app
  ```
- [ ] 👤 **Open Merge Request on GitLab**:
  - Target: `fdroid/fdroiddata:master` from `<YOUR_GITLAB_USERNAME>/fdroiddata:com.tukiwatch.app`.
  - Title: `New App: com.tukiwatch.app`
  - Description: Complete the standard F-Droid MR template (confirm FLOSS compliance, state no tracking/ads, specify that it is the author submitting).

---

## Phase 8: Review Interaction, CI Monitoring & Acceptance

Follow up on the Merge Request until merged into `fdroiddata`.

### Tasks
- [ ] 👤 **Monitor GitLab CI Pipeline**:
  - Check the automated CI pipeline on your MR for lint or build errors.
- [ ] 🤖 / 👤 **Troubleshoot Review Feedback**:
  - If maintainers request adjustments, Antigravity can analyze and patch the recipe YAML.
- [ ] 👤 **Maintainer Approval & Merge**:
  - Once approved, an F-Droid maintainer merges the branch into `fdroiddata/master`.

---

## Phase 9: Post-Merge Verification & Repository Maintenance

Verify indexing and make F-Droid available to end users.

### Tasks
- [ ] 👤 **Monitor Build Cycle**:
  - After MR merge, wait for the F-Droid build cycle (typically 24–48 hours) to build and sign the app.
- [ ] 👤 **Confirm F-Droid Listing**:
  - Verify app appears on `https://f-droid.org/packages/com.tukiwatch.app/` and in the F-Droid client.
- [ ] 🤖 **Add F-Droid Badge to README**:
  - Update `README.md` and website with official badge:
    ```markdown
    [![Get it on F-Droid](https://fdroid.gitlab.io/artwork/badge/get-it-on.svg)](https://f-droid.org/packages/com.tukiwatch.app/)
    ```
- [ ] 🤖 **Automate Future Releases**:
  - For future updates, keep `fastlane/metadata/android/en-US/changelogs/<newCode>.txt` updated, tag releases consistently, and F-Droid's checkupdates bot will automatically generate subsequent build metadata.
