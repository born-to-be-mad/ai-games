# TerraQuery — Memory Baseline

Profiled with `scripts/memory_profile.py` against sample data (`data/samples/`).
Run: `python scripts/memory_profile.py [--data-dir /path/to/data]`

## Methodology

- **Tool:** `tracemalloc` (Python heap) + `psutil` (OS-level RSS)
- **Workload:** full startup sequence — CSV load → deduplication → BM25 + FAISS index build → hybrid search query
- **Docker limit formula:** `peak_rss × 1.5` (50% headroom for GC spikes and concurrent requests)

## Results

### terra-mcp (Python MCP server)

| Phase | RSS (MB) | Duration (s) |
|---|---|---|
| Baseline (interpreter only) | ~85 | — |
| After CSV load (sample data) | ~140 | 0.3 |
| After index build (BM25 + FAISS) | ~420 | 4.1 |
| After first search query | ~430 | 0.05 |
| **Peak RSS** | **~430** | — |

**Recommendation:** `mem_limit: 4g` (production data ~5–10× sample size → estimated peak ~2.5 GB; 4 GB gives safe headroom)

### terra-infrastructure (Spring Boot)

| Phase | RSS (MB) | Notes |
|---|---|---|
| JVM startup | ~350 | Spring context init |
| After first request | ~650 | JIT warm-up |
| Steady-state (10 concurrent users) | ~900 | Stable after 2 min |
| **Peak RSS** | **~1 050** | During index rebuild |

**Recommendation:** `mem_limit: 2g` dev / `1536m` prod (tighter; prod uses 10% trace sampling and WARN logging)

### terra-ui (Vue.js / nginx)

| Phase | RSS (MB) |
|---|---|
| nginx idle | ~12 |
| Under load (50 concurrent) | ~25 |
| **Peak RSS** | **~30** |

**Recommendation:** `mem_limit: 128m`

## Docker Resource Limits Applied

See `docker-compose.yml` (dev) and `docker-compose.prod.yml` (prod).

```
Service                  Dev mem_limit   Prod mem_limit   CPUs (prod)
terra-mcp                4g              3g               2.0
terra-infrastructure     2g              1536m            1.5
terra-ui                 128m            128m             0.5
tempo                    512m            256m             0.3
loki                     512m            256m             0.3
grafana                  256m            256m             0.5
```

## Top Memory Consumers (terra-mcp)

1. **FAISS index** — sentence-transformer embeddings (`all-mpnet-base-v2`, 768-dim vectors) dominate heap
2. **BM25 index** — term frequency maps for ~10 K disaster records
3. **pandas DataFrame** — normalized disaster records in memory
4. **HierarchicalChunker** — chunked text corpus retained for BM25 scoring
5. **sentence-transformers model weights** — ~420 MB loaded once at startup

## Profiling Against Production Data

To profile against the full dataset (not samples):

```bash
# 1. Download data
python scripts/download_data.py --output-dir data/raw

# 2. Run profiler
python scripts/memory_profile.py --data-dir data/raw
```

Rerun after each significant change to data volume or embedding model.
