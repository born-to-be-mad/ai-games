# Mutation Survivors

Surviving mutants from the most recent `mutmut` run on `terra-mcp`.  
Documented here to distinguish **intentionally surviving** mutants from test-coverage gaps.

A "survivor" is acceptable when:
- The mutated code path is unreachable in the current data model
- The mutation produces equivalent behaviour (the mutated code is observationally identical)
- The mutation exercises an error/fallback path that is intentionally not tested with real I/O

---

## How to regenerate this list

```bash
cd ai-architect/terra-query/terra-mcp
mutmut run
mutmut results          # shows killed / survived / suspicious
mutmut show <id>        # inspect a specific survivor
```

---

## Known survivors

> Initially empty — populate after the first nightly CI run adds results.

| ID | File | Line | Mutation | Rationale |
|----|------|------|----------|-----------|
| — | — | — | — | No survivors recorded yet |

---

## Kill-rate target

`≥ 65%` — enforced in CI by `.github/workflows/mutation-python.yml`.  
Any drop below 65% fails the nightly build and requires either:
1. Adding a test that kills the surviving mutant, or
2. Adding the survivor to this table with a documented rationale.
