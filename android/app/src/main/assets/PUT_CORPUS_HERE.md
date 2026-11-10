Two files belong in this directory:

  corpus.jsonl        required — output of tools/fetch_corpus.py + tools/tag_evidence.py
  embedder.tflite     optional — MediaPipe text embedder, enables semantic rerank
  corpus_vectors.bin  optional — output of tools/embed_corpus.py, precomputes the vectors

Only corpus.jsonl is needed to run. Without the .tflite the app uses BM25 alone
and says so on the Ask tab.
