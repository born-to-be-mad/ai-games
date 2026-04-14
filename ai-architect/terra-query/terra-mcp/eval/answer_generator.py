"""Answer generator: retrieves contexts from MCP, produces answers via generator LLM."""
from __future__ import annotations

import logging
from dataclasses import dataclass, field
from typing import Callable, Protocol, runtime_checkable

logger = logging.getLogger(__name__)


@dataclass
class EvalSample:
    """One evaluation sample ready for RAGAS scoring."""

    question_id: str
    question: str
    ground_truth: str
    contexts: list[str]
    answer: str


@runtime_checkable
class ContextRetriever(Protocol):
    """Pluggable context-retrieval strategy (injectable for testing)."""

    def retrieve(self, question: str) -> list[str]:
        ...


class McpDirectRetriever:
    """Retrieves contexts by importing terra-mcp search modules directly (no HTTP)."""

    def __init__(self, repo, engine, top_k: int = 5) -> None:
        self._repo = repo
        self._engine = engine
        self._top_k = top_k

    def retrieve(self, question: str) -> list[str]:
        if self._engine is None:
            logger.warning("No search engine — contexts will be empty for question: %s", question)
            return []
        results = self._engine.search(question)
        texts: list[str] = []
        df = self._repo.df
        for r in results[: self._top_k]:
            record_id = r.get("record_id", "")
            text = r.get("text", "")
            if not df.empty and "event_id" in df.columns:
                row_match = df[df["event_id"] == record_id]
                if not row_match.empty:
                    row = row_match.iloc[0]
                    text = _row_to_context(row)
            if text:
                texts.append(text)
        return texts


def _row_to_context(row) -> str:
    """Convert a disaster DataFrame row to a readable context string."""
    import pandas as pd

    def _fmt(val, suffix="") -> str:
        try:
            if pd.isna(val):
                return "unknown"
        except (TypeError, ValueError):
            pass
        if suffix:
            return f"{val}{suffix}"
        return str(val)

    dtype = _fmt(row.get("disaster_type", "unknown")).title()
    country = _fmt(row.get("country", "unknown"))
    region = _fmt(row.get("region", "unknown"))
    year = "unknown"
    try:
        ts = row.get("start_date")
        if ts is not None and not pd.isna(ts):
            year = str(pd.Timestamp(ts).year)
    except Exception:
        pass
    deaths = _fmt(row.get("deaths"), " deaths")
    damage = _fmt(row.get("economic_damage_usd"), " USD damage")
    affected = _fmt(row.get("affected"), " affected")
    mag = row.get("magnitude")
    mag_str = f", magnitude {mag}" if mag and str(mag) != "unknown" else ""

    return (
        f"{dtype} in {country} ({region}), {year}. "
        f"Deaths: {deaths}. Affected: {affected}. Economic damage: {damage}{mag_str}."
    )


class AnswerGenerator:
    """Generates LLM answers for questions using retrieved contexts.

    Uses the generator LLM (OpenAI gpt-4o-mini) to produce answers — separate
    from the evaluator LLM (Anthropic claude-haiku) to eliminate self-eval bias.
    """

    _PROMPT_TEMPLATE = (
        "You are a natural disaster information assistant. "
        "Answer the question using ONLY the provided context. "
        "If the context does not contain enough information, say so explicitly.\n\n"
        "Context:\n{context}\n\n"
        "Question: {question}\n\n"
        "Answer:"
    )

    def __init__(
        self,
        retriever: ContextRetriever,
        generator_llm_fn: Callable[[str], str],
    ) -> None:
        self._retriever = retriever
        self._generate = generator_llm_fn

    def generate_sample(self, question_id: str, question: str, ground_truth: str) -> EvalSample:
        contexts = self._retriever.retrieve(question)
        if not contexts:
            logger.warning("No contexts retrieved for question %s: %s", question_id, question)
            answer = "Insufficient context to answer this question."
        else:
            prompt = self._PROMPT_TEMPLATE.format(
                context="\n\n".join(f"[{i+1}] {c}" for i, c in enumerate(contexts)),
                question=question,
            )
            answer = self._generate(prompt)
        return EvalSample(
            question_id=question_id,
            question=question,
            ground_truth=ground_truth,
            contexts=contexts,
            answer=answer,
        )

    def generate_batch(
        self,
        entries: list[dict],
    ) -> list[EvalSample]:
        samples: list[EvalSample] = []
        for entry in entries:
            qid = entry["id"]
            logger.info("Generating answer for %s", qid)
            try:
                sample = self.generate_sample(qid, entry["question"], entry["ground_truth"])
                samples.append(sample)
            except Exception as exc:
                logger.error("Failed to generate sample for %s: %s", qid, exc)
        return samples


def build_openai_generator_fn(model: str, api_key: str) -> Callable[[str], str]:
    """Return a callable that sends a prompt to OpenAI and returns the text response."""

    def _call(prompt: str) -> str:
        from openai import OpenAI

        client = OpenAI(api_key=api_key)
        response = client.chat.completions.create(
            model=model,
            messages=[{"role": "user", "content": prompt}],
            temperature=0.0,
            max_tokens=512,
        )
        return response.choices[0].message.content or ""

    return _call
