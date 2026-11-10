<p align="center"><img src="assets/icon-512.png" width="128" alt="Peptide Advisor icon"></p>

<h1 align="center">Peptide Advisor</h1>

<p align="center">A personal research tool for peptides: literature answers that say what kind of evidence is behind every claim, a reconstitution calculator, a dosing log, and a per-lot vendor assay record.</p>

---

## What's here

| Folder | What it is | Status |
|---|---|---|
| [`web/`](web/) | Single-file app — Ask, Calculator, Log, Vendors | Working; tested |
| [`android/`](android/) | Native Kotlin + Jetpack Compose app with an offline, evidence-tagged literature corpus | Source only; not yet compiled |
| [`assets/`](assets/) | Icon source (SVG) and exports | — |

## The idea

Most peptide information online doesn't distinguish a randomized human trial from four rat studies. This app's job is to make that distinction impossible to miss. Every answer tags its sources by study design — **Human RCT**, **Human observational**, **Review**, **Animal**, **In vitro** — and is instructed never to convert an animal dose into a human one.

## Web app

`web/index.html` was built to run **inside the Claude app as an artifact**, which supplies two things it depends on:

- **Ask** calls the Claude API with no key in the page — that only works inside Claude.
- **Saving** uses Claude's artifact storage.

Opened anywhere else (a browser, GitHub Pages), the **Calculator works fully**, but Ask will fail and the Log and Vendors tabs won't persist between visits. Making it standalone means adding your own API proxy and switching storage to `localStorage`.

## Android app

See [`android/README.md`](android/README.md) and [`android/BUILD.md`](android/BUILD.md). Requires Android Studio; the launcher icon set (adaptive, round, and monochrome for themed icons) is already in `android/app/src/main/res/mipmap-*`.

## The calculator

A U-100 syringe reads 100 units to 1 mL. Everything follows from that:

```
5 mg vial + 2 mL bacteriostatic water → 2.5 mg/mL → 25 mcg per unit → 250 mcg = 10 units
```

The math is covered by unit tests in `android/app/src/test/`.

## Scope

This reads literature and does arithmetic. It doesn't know your medical history and isn't a substitute for a clinician who does. Several compounds discussed are prescription drugs; most others have not been evaluated for human use by any regulator.
