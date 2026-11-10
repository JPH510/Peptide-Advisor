# Peptide Advisor — Android

A single-user Android app: grounded literature Q&A over a corpus you build yourself, a dosing log with a reconstitution calculator, and a per-lot vendor assay record.

Kotlin + Jetpack Compose, Room, minSdk 26, targetSdk 36. No backend, no accounts, no analytics. Built for a Galaxy S25 Ultra but nothing here is device-specific.

---

## Setup

**1. Build the corpus** — no API key needed.

```bash
cd tools
python3 fetch_corpus.py --out corpus.jsonl --per-compound 120
```

Pulls PubMed abstracts and ClinicalTrials.gov registry records for every compound in the `COMPOUNDS` dict at the top of the script. Edit that dict — the aliases matter, because PubMed indexes these inconsistently, and `"BPC 157"` and `"BPC-157"` are different queries.

Expect ~1,500–3,000 records, about 10 minutes. Pass `--ncbi-key` if you have one.

**2. Tag it.**

```bash
export ANTHROPIC_API_KEY=sk-ant-...
python3 tag_evidence.py --in corpus.jsonl
```

Classifies every record by study design: human RCT, human controlled, human observational, case report, animal, in vitro, review. Batches of 8, writes through after each batch so an interrupted run isn't wasted. Idempotent.

This pass is the product. Everything else is plumbing around it.

**3. Drop `corpus.jsonl` into `app/src/main/assets/`.** That's the minimum to run.

**4. Optional — semantic retrieval.** Download a MediaPipe text embedder `.tflite`, save it as `app/src/main/assets/embedder.tflite`, and optionally precompute:

```bash
pip install mediapipe
python3 embed_corpus.py --in corpus.jsonl --model embedder.tflite --out corpus_vectors.bin
```

Copy both into `assets/`. Skip this entirely and the app runs lexical-only and says so on the Ask tab.

**5. Build and install**, then paste an API key into Settings.

---

## How the Ask tab works

Retrieval is two-stage:

1. **BM25** over the corpus, indexed at launch. Compound names are exact-match terms, which is what BM25 is good at and what pure vector search tends to blur.
2. **Semantic rerank** of the top 50 down to 8, via MediaPipe's `TextEmbedder`.

The same `.tflite` runs in Python and on the device, so corpus vectors can be precomputed and shipped and still live in the same space as the query vector the phone computes at runtime. `CorpusStore` checks the row count against the corpus and silently falls back to on-the-fly embedding if they disagree — so a stale `corpus_vectors.bin` degrades instead of returning nonsense.

Lexical carries 65% of the blended score. Near-ties break toward the stronger evidence tier.

The retrieved 8 go to the API with a system prompt (`AskEngine.SYSTEM_PROMPT`) that forbids stepping outside them, requires `[S1]`-style inline markers, requires species and sample size on every effect claim, and specifically forbids extrapolating a human dose from animal data. Markers are parsed back out so the UI greys out sources that were retrieved but never used — if the model cites nothing, that's visible.

When retrieval returns nothing, the app says so instead of asking the model anyway.

---

## The calculator

A U-100 syringe reads 100 units to 1 mL. Everything routes through that identity:

```
5 mg vial + 2 mL bacteriostatic water
  → 2.5 mg/mL
  → 25 mcg per unit
  → a 250 mcg dose is 10 units
```

The result renders as a drawn barrel with gradations, not a number in a box, because that's how you'll check it at arm's length in bad light.

Two warnings, both about measurement error rather than the compound:

- **Under 2 units** — too small a mark to read reliably. Offers a diluent volume that puts the same dose at 10 units.
- **Over 100 units** — won't fit in one syringe.

`app/src/test/` covers the math across seven mixes, both thresholds, the dose↔units round trip, the suggested-diluent solver, citation parsing, and the tokenizer's compound-name handling. Pure JVM tests — `./gradlew test`, no device needed.

---

## Notes on the Android specifics

**API key** — AES-256-GCM under a non-exportable Keystore key, wrapping a plain SharedPreferences blob. Deliberately *not* `EncryptedSharedPreferences`, which is deprecated and is the obvious thing to reach for. `SecretStore` is about forty lines and has no dependency.

**Dynamic colour is off.** On One UI, Material You would repaint the evidence tier badges from your wallpaper, and those colours carry meaning — teal is human evidence, amber is animal.

**Backup is off** for the database and the secrets file. The dose log doesn't belong in cloud backup.

**`noCompress` for `.tflite` and `.bin`** so MediaPipe can memory-map the model instead of unpacking it.

---

## Layout

```
tools/
  fetch_corpus.py       PubMed + ClinicalTrials.gov → corpus.jsonl
  tag_evidence.py       batch evidence-tier classification
  embed_corpus.py       optional vector precompute
app/src/main/java/com/peptideadvisor/
  core/
    Reconstitution.kt   dosing math, no Android imports
    EvidenceTier.kt     tier model, ordering, plain-language caveats
    Corpus.kt           record type, tokenizer, BM25
    CorpusStore.kt      asset loading, embedder, two-stage retrieval
    AskEngine.kt        grounding prompt, citation parsing
    ClaudeClient.kt     OkHttp
    SecretStore.kt      Keystore AES-GCM
  data/                 Room: Compound, Vial, Dose, Vendor, LabReport
  ui/ask | log | vendors | settings | components | theme
app/src/test/           JVM unit tests
```

---

## Scope

This reads literature and does arithmetic. It has no knowledge of your medical history, and a well-cited answer about a compound is not advice about you — several of these compounds are prescription drugs, and most of the rest have never been evaluated for human use by any regulator. Worth having a clinician who knows what you're taking.
