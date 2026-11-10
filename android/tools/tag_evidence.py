#!/usr/bin/env python3
"""
Assign an evidence tier to every record in corpus.jsonl.

This is the pass that makes the app worth building. It runs once, offline,
so the phone never has to guess at study quality at query time.

    export ANTHROPIC_API_KEY=sk-ant-...
    python3 tag_evidence.py --in corpus.jsonl --out corpus.jsonl

Idempotent: records already tagged are skipped unless --retag is passed.
"""

import argparse
import json
import os
import sys
import time
import urllib.request

API_URL = "https://api.anthropic.com/v1/messages"
MODEL = "claude-sonnet-5"

VALID_TIERS = {
    "human_rct", "human_controlled", "human_observational",
    "case_report", "animal", "in_vitro", "review", "unknown",
}

SYSTEM = """You classify biomedical abstracts by study design. You are cataloguing, not evaluating claims.

For each record, return:
  tier — exactly one of: human_rct, human_controlled, human_observational, case_report, animal, in_vitro, review, unknown
  species — the organism studied: "human", "rat", "mouse", "rabbit", "dog", "cell line", or a short noun phrase
  sample_size — integer number of subjects if the abstract states it, else null
  rationale — under 15 words, naming the design feature that decided the tier

Rules:
- human_rct requires explicit randomization in human subjects.
- human_controlled: human trial with a comparison group but no stated randomization.
- human_observational: cohort, cross-sectional, case-control, or uncontrolled human series.
- case_report: one to a few patients, no comparison group.
- animal: any in vivo non-human work. This applies even when the abstract frames results as clinically relevant.
- in_vitro: cells, tissue, or biochemical assays only.
- review: narrative review, systematic review, or meta-analysis.
- unknown: the abstract genuinely does not say.

Mixed designs take the strongest tier actually performed. An animal study with a cell-culture component is animal.

Return ONLY a JSON array, one object per input record, each with keys: id, tier, species, sample_size, rationale. No prose, no markdown fences."""


def call_api(key, payload_text, retries=4):
    body = json.dumps({
        "model": MODEL,
        "max_tokens": 2000,
        "system": SYSTEM,
        "messages": [{"role": "user", "content": payload_text}],
    }).encode()

    request = urllib.request.Request(
        API_URL,
        data=body,
        headers={
            "content-type": "application/json",
            "x-api-key": key,
            "anthropic-version": "2023-06-01",
        },
    )

    for attempt in range(retries):
        try:
            with urllib.request.urlopen(request, timeout=120) as resp:
                data = json.loads(resp.read())
            return "".join(
                block.get("text", "")
                for block in data.get("content", [])
                if block.get("type") == "text"
            )
        except urllib.error.HTTPError as exc:
            if exc.code in (429, 500, 502, 503, 529) and attempt < retries - 1:
                wait = 2 ** (attempt + 1)
                print(f"   {exc.code}, retrying in {wait}s", file=sys.stderr)
                time.sleep(wait)
                continue
            print(f"   ! HTTP {exc.code}: {exc.read()[:200]}", file=sys.stderr)
            return None
        except Exception as exc:  # noqa: BLE001
            if attempt < retries - 1:
                time.sleep(2 ** (attempt + 1))
                continue
            print(f"   ! {exc}", file=sys.stderr)
            return None
    return None


def parse_array(text):
    if not text:
        return []
    cleaned = text.strip()
    if cleaned.startswith("```"):
        cleaned = cleaned.split("```")[1]
        if cleaned.startswith("json"):
            cleaned = cleaned[4:]
    start, end = cleaned.find("["), cleaned.rfind("]")
    if start == -1 or end == -1:
        return []
    try:
        parsed = json.loads(cleaned[start:end + 1])
        return parsed if isinstance(parsed, list) else []
    except json.JSONDecodeError:
        return []


def batch_payload(records):
    lines = []
    for record in records:
        lines.append(json.dumps({
            "id": record["id"],
            "title": record["title"],
            # Enough to classify design without burning tokens on full text.
            "abstract": (record.get("abstract") or "")[:1400],
        }, ensure_ascii=False))
    return "\n".join(lines)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="infile", default="corpus.jsonl")
    parser.add_argument("--out", dest="outfile", default=None)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--retag", action="store_true",
                        help="re-classify records that already have a tier")
    args = parser.parse_args()
    outfile = args.outfile or args.infile

    key = os.environ.get("ANTHROPIC_API_KEY")
    if not key:
        sys.exit("Set ANTHROPIC_API_KEY first.")

    records = []
    with open(args.infile, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                records.append(json.loads(line))

    by_id = {r["id"]: r for r in records}
    pending = [
        r for r in records
        if args.retag or r.get("tier") in (None, "", "unknown")
    ]
    print(f"{len(records)} records, {len(pending)} to tag")

    tagged = 0
    for i in range(0, len(pending), args.batch):
        chunk = pending[i:i + args.batch]
        print(f"[{i + len(chunk)}/{len(pending)}]")

        result = parse_array(call_api(key, batch_payload(chunk)))
        for item in result:
            record = by_id.get(item.get("id"))
            if not record:
                continue
            tier = item.get("tier")
            if tier not in VALID_TIERS:
                continue
            record["tier"] = tier
            record["species"] = item.get("species")
            record["tierRationale"] = item.get("rationale")
            size = item.get("sample_size")
            record["sampleSize"] = size if isinstance(size, int) else None
            tagged += 1

        # Write through after each batch so an interrupted run isn't wasted.
        with open(outfile, "w", encoding="utf-8") as fh:
            for record in records:
                slim = {k: v for k, v in record.items() if not k.startswith("_")}
                fh.write(json.dumps(slim, ensure_ascii=False) + "\n")

    counts = {}
    for record in records:
        counts[record.get("tier", "unknown")] = counts.get(record.get("tier", "unknown"), 0) + 1

    print(f"\ntagged {tagged} records → {outfile}")
    for tier, count in sorted(counts.items(), key=lambda kv: -kv[1]):
        print(f"  {tier:22} {count}")


if __name__ == "__main__":
    main()
