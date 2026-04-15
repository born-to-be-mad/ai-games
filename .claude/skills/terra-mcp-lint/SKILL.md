---
name: terra-mcp-lint
description: Fix Python formatting and type errors in terra-query/terra-mcp. Use when ruff format or mypy CI jobs fail, or when the user says "fix lint", "fix formatting", "fix types", or "fix mypy" for terra-mcp.
---

Fix all ruff formatting and mypy type errors in terra-query/terra-mcp.

Working directory: `ai-architect/terra-query/terra-mcp/` (relative to repo root).

## Steps

1. Install tools if missing:
   ```
   pip install ruff mypy --quiet
   ```

2. Auto-format all Python files:
   ```
   ruff format .
   ```

3. Run ruff lint (check only — do NOT auto-fix; report errors for manual review):
   ```
   ruff check .
   ```
   If lint errors exist, fix them manually in the affected files before continuing.

4. Run mypy type check:
   ```
   mypy server.py tools/ search/ validation/ --ignore-missing-imports
   ```
   Fix every reported error. Common patterns in this codebase:
   - `Optional[T]` used in arithmetic → add `and val is not None` guard
   - `Any | None` passed to typed function → add explicit `val is not None` branch
   - Methods accidentally indented inside a module-level function → move to correct class scope
   - Missing optional arg in Pydantic model constructor → pass explicit `field=None`
   - `str` passed where `Literal[...]` expected → use `cast(Literal[...], value)`

5. Re-run ruff format to catch any formatting changes introduced by the mypy fixes:
   ```
   ruff format .
   ```

6. Final verification — both must exit 0:
   ```
   ruff check . && ruff format --check . && mypy server.py tools/ search/ validation/ --ignore-missing-imports
   ```

7. Commit with:
   ```
   🎨 style(terra-mcp): apply ruff formatting
   ```
   or if mypy fixes were needed:
   ```
   🐛 fix(terra-mcp): resolve ruff/mypy errors
   ```
   Stage only `ai-architect/terra-query/terra-mcp/` files.
