#!/usr/bin/env python3
"""
Precompute corpus embeddings so the phone doesn't have to.

Optional. Without this, the app embeds candidate titles on the fly at query time,
which is fine — this just makes retrieval faster and lets the semantic stage see
the whole corpus instead of only the BM25 shortlist.

The point of doing it this way: MediaPipe runs the *same* .tflite model here and
on the device, so these vectors live in the same space as the query vector the
phone computes. That parity is what makes precomputing safe.

    pip install mediapipe
    python3 embed_corpus.py --in corpus.jsonl --model embedder.tflite \
        --out corpus_vectors.bin

Copy both embedder.tflite and corpus_vectors.bin into app/src/main/assets/.
Re-run whenever corpus.jsonl changes — the app checks the row count and falls
back to on-the-fly embedding if they disagree.
"""

import argparse
import json
import struct
import sys


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--in", dest="infile", default="corpus.jsonl")
    parser.add_argument("--model", default="embedder.tflite")
    parser.add_argument("--out", dest="outfile", default="corpus_vectors.bin")
    args = parser.parse_args()

    try:
        from mediapipe.tasks import python as mp_python
        from mediapipe.tasks.python import text as mp_text
    except ImportError:
        sys.exit("pip install mediapipe first.")

    records = []
    with open(args.infile, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line:
                records.append(json.loads(line))

    if not records:
        sys.exit(f"{args.infile} has no records.")

    options = mp_text.TextEmbedderOptions(
        base_options=mp_python.BaseOptions(model_asset_path=args.model),
        # Must match setL2Normalize(true) in CorpusStore.createEmbedder().
        l2_normalize=True,
        quantize=False,
    )

    vectors = []
    dim = None
    with mp_text.TextEmbedder.create_from_options(options) as embedder:
        for i, record in enumerate(records):
            # Titles only — matches what the device embeds when it reranks.
            # Sentence embedders degrade badly on full abstracts.
            title = record.get("title") or ""
            result = embedder.embed(title)
            floats = list(result.embeddings[0].embedding)

            if dim is None:
                dim = len(floats)
            elif len(floats) != dim:
                sys.exit(f"Inconsistent embedding width at row {i}: {len(floats)} vs {dim}")

            vectors.append(floats)
            if (i + 1) % 250 == 0:
                print(f"  {i + 1}/{len(records)}")

    # Layout must match CorpusStore.readVectors():
    #   int32 count, int32 dim, then count * dim float32, little-endian,
    #   in corpus.jsonl line order.
    with open(args.outfile, "wb") as fh:
        fh.write(struct.pack("<ii", len(vectors), dim))
        for vector in vectors:
            fh.write(struct.pack(f"<{dim}f", *vector))

    size_mb = (8 + len(vectors) * dim * 4) / (1024 * 1024)
    print(f"\nwrote {len(vectors)} x {dim} vectors to {args.outfile} ({size_mb:.1f} MB)")
    print("Copy this and the model into app/src/main/assets/.")


if __name__ == "__main__":
    main()
