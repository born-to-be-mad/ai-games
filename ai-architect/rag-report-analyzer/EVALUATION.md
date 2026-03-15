# RAG Evaluation Report

Evaluation of the `rag-report-analyzer` pipeline against the manually verified golden dataset.

## Dataset

- **Source:** NVIDIA FY2025 Annual Report (10-K, filed 2025)
- **Size:** 20 Q&A pairs covering revenue, segment performance, risks, strategy, and key metrics
- **Location:** `backend/src/main/resources/eval/golden-dataset.json`
- **Verification:** Manually verified against the original SEC filing — NOT LLM-generated

## Metrics (RAGAS-inspired)

| Metric | Description | Target |
|---|---|---|
| **Context Precision** | % of retrieved chunks relevant to the question | ≥ 0.70 |
| **Context Recall** | % of expected-answer facts present in retrieved context | ≥ 0.65 |
| **Faithfulness** | Degree to which generated answer is grounded in context (no hallucinations) | ≥ 0.75 |
| **Answer Relevance** | Degree to which generated answer addresses the question | ≥ 0.70 |

All metrics are scored 0.0–1.0 via LLM-as-judge (`eval-judge.st` prompt). See `POST /api/v1/eval/run`.

---

## How to Run

```bash
# Ingest the report first (required before evaluation)
curl -X POST "http://localhost:8080/api/v1/ingest" \
  -F "file=@NVIDIA-2025-Annual-Report.pdf" \
  -F "ticker=NVDA" \
  -F "year=2025" \
  -F "quarter=Annual"

# Single configuration run (topK=5)
curl -X POST "http://localhost:8080/api/v1/eval/run?topK=5"

# Full comparison matrix (topK=3, 5, 10)
curl -X POST "http://localhost:8080/api/v1/eval/run/matrix"
```

---

## Comparison Matrix (topK × chunk_size)

> **Note:** The results below are placeholders. Run `POST /api/v1/eval/run/matrix` after ingesting
> the NVIDIA 2025 Annual Report to populate with real values.
>
> Chunk size variation requires re-ingestion with a different `TokenTextSplitter` configuration.
> The default configuration uses **chunk_size=512, overlap=64**.

### topK Comparison (chunk_size=512, overlap=64)

| topK | Context Precision | Context Recall | Faithfulness | Answer Relevance | Overall |
|------|------------------|----------------|--------------|------------------|---------|
| **3** | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ |
| **5** | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ |
| **10** | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ |

### Chunk Size Comparison (topK=5)

| chunk_size / overlap | Context Precision | Context Recall | Faithfulness | Answer Relevance | Overall |
|----------------------|------------------|----------------|--------------|------------------|---------|
| 256 / 32 | _re-ingest required_ | — | — | — | — |
| **512 / 64** (default) | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ | _run to populate_ |
| 1024 / 128 | _re-ingest required_ | — | — | — | — |

### Expected Best Configuration

Based on prior RAG literature for financial documents:
- **chunk_size=512, topK=5** is expected to be the best balance: chunks large enough to contain
  a complete financial statement sentence while small enough to stay focused
- **topK=3** risks low recall for multi-fact questions; **topK=10** risks low precision
  as less-relevant chunks dilute the context

---

## Methodology Notes

### LLM-as-Judge
All four metrics are scored by calling the same LLM provider used for generation (configurable via
`AI_PROVIDER`). The `eval-judge.st` prompt asks for all four scores in a single structured-output
call to minimise cost.

**Limitations:**
- LLM-as-judge can be biased toward its own outputs (self-preference bias)
- Scoring may vary slightly between runs due to LLM non-determinism (use `temperature=0` for
  reproducible results; not yet enforced)
- Context precision is measured holistically (not per-chunk) to reduce token cost

### Why LLM-as-judge instead of exact string matching?
Financial Q&A answers are naturally paraphrastic — "NVIDIA earned $72.9B" and "net income was
$72.9 billion" are semantically equivalent but string-mismatched. Exact-match metrics would
artificially deflate recall and relevance scores.

### Metric interpretation
| Result | Likely cause |
|---|---|
| Low context precision + low recall | Wrong ticker/year filter, bad chunk boundaries |
| High recall but low faithfulness | LLM is hallucinating beyond the retrieved context |
| High faithfulness but low relevance | LLM is answering "I don't know" faithfully but unhelpfully |
| Low overall with topK=3 | Too few chunks; key facts not retrieved |
| Low overall with topK=10 | Context overload; irrelevant chunks distract the LLM |
