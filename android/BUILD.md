# Building

I could not compile this for you — producing an APK needs the Android SDK,
a Gradle distribution, and network access to resolve dependencies. What follows
is verified configuration, not a verified build. Expect to fix a few things on
first sync; that is normal for a project this size.

## What's missing from this folder

`gradle/wrapper/gradle-wrapper.jar` — a binary I can't fabricate. Get it either way:

- **Android Studio**: open the project folder. It offers to generate the wrapper. Accept.
- **Command line**, if you have Gradle installed: `gradle wrapper --gradle-version 8.13`

You also need an app icon. The manifest references `@mipmap/ic_launcher`; Android
Studio's Image Asset tool (right-click `res` → New → Image Asset) generates it, or
drop any launcher icon set into `res/mipmap-*`.

## First build

```bash
# 1. Corpus — required, the app shows an empty state without it
cd tools
python3 fetch_corpus.py --out corpus.jsonl --per-compound 120
export ANTHROPIC_API_KEY=sk-ant-...
python3 tag_evidence.py --in corpus.jsonl
cp corpus.jsonl ../app/src/main/assets/

# 2. Tests first — pure JVM, no device, no SDK emulator needed
cd ..
./gradlew test

# 3. Install to the S25 Ultra over USB (developer options + USB debugging on)
./gradlew installDebug
```

If `./gradlew test` passes, the dosing math and retrieval logic are correct and
any remaining problems are UI wiring.

## Version pins

Set in the root `build.gradle.kts`. These were current as of writing; if Studio
complains, take its suggested upgrade rather than fighting it.

| Component | Version |
|---|---|
| Android Gradle Plugin | 8.11.0 |
| Kotlin | 2.1.20 |
| KSP | 2.1.20-2.0.0 |
| Gradle | 8.13 |
| Compose BOM | 2025.06.00 |
| Room | 2.7.1 |
| compileSdk / targetSdk | 36 |
| minSdk | 26 |

## Likely first-sync friction

- **KSP version must match Kotlin exactly.** `2.1.20-2.0.0` pairs with Kotlin
  `2.1.20`. If you bump one, bump both.
- **`compileSdk = 36`** needs Android SDK Platform 36 installed via SDK Manager.
- **MediaPipe** pulls native `.so` files and adds ~20 MB. If you skip semantic
  retrieval entirely, delete the `tasks-text` dependency and the embedder block
  in `CorpusStore.createEmbedder()` — BM25 alone works and the app already
  handles the embedder being absent.
