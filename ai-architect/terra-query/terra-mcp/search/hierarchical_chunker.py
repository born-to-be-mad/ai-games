"""HierarchicalChunker: adaptive 3-tier chunking for disaster records."""

from __future__ import annotations

import re
from dataclasses import dataclass
from typing import Any


@dataclass
class ChunkPair:
    child_id: str
    child_text: str  # ≤128 tokens — goes into vector index
    parent_id: str
    parent_text: str  # ≤512 tokens — sent to LLM on retrieval
    metadata: dict  # original record metadata


class HierarchicalChunker:
    """
    Adaptive 3-tier chunking strategy:
    - < MIN_CHILD_SIZE tokens: record is its own parent AND child (self-parent)
    - < PARENT_SIZE tokens: single parent, split into children normally
    - ≥ PARENT_SIZE tokens: full hierarchy — split into parents, then children

    Also enriches bare CSV rows into natural-language sentences before embedding.
    """

    PARENT_SIZE = 512
    CHILD_SIZE = 128
    MIN_CHILD_SIZE = 32
    PARENT_OVERLAP = 64
    CHILD_OVERLAP = 16

    def chunk(self, text: str, metadata: dict | None = None) -> list[ChunkPair]:
        meta = metadata or {}
        token_count = self._token_count(text)

        if token_count < self.MIN_CHILD_SIZE:
            return [
                ChunkPair(
                    child_id="c_0_0",
                    child_text=text,
                    parent_id="p_0",
                    parent_text=text,
                    metadata=meta,
                )
            ]

        if token_count < self.PARENT_SIZE:
            children = self._split(text, self.CHILD_SIZE, self.CHILD_OVERLAP)
            return [
                ChunkPair(
                    child_id=f"c_0_{i}",
                    child_text=child,
                    parent_id="p_0",
                    parent_text=text,
                    metadata=meta,
                )
                for i, child in enumerate(children)
            ]

        # Full hierarchy
        parents = self._split(text, self.PARENT_SIZE, self.PARENT_OVERLAP)
        pairs: list[ChunkPair] = []
        for p_idx, parent in enumerate(parents):
            children = self._split(parent, self.CHILD_SIZE, self.CHILD_OVERLAP)
            for c_idx, child in enumerate(children):
                pairs.append(
                    ChunkPair(
                        child_id=f"c_{p_idx}_{c_idx}",
                        child_text=child,
                        parent_id=f"p_{p_idx}",
                        parent_text=parent,
                        metadata=meta,
                    )
                )
        return pairs

    def enrich_text(self, record: dict[str, Any]) -> str:
        """
        Convert structured record to natural language for better embedding quality.
        A bare CSV row like 'Flood,BGD,1998,1050,30000000' embeds poorly.
        Enriched: 'A flood in Bangladesh in 1998 killed 1,050 people and affected 30,000,000.'
        """
        dtype = str(record.get("disaster_type") or "disaster").title()
        country = str(
            record.get("country") or record.get("country_iso3") or "unknown location"
        )
        start = record.get("start_date")
        end = record.get("end_date")
        deaths = record.get("deaths")
        affected = record.get("affected")
        damage = record.get("economic_damage_usd")
        magnitude = record.get("magnitude")
        subtype = record.get("subtype")

        # Date phrase
        if start and not _is_na(start):
            year = _extract_year(start)
            if end and not _is_na(end) and str(end) != str(start):
                end_year = _extract_year(end)
                date_phrase = (
                    f"from {year} to {end_year}" if year != end_year else f"in {year}"
                )
            else:
                date_phrase = f"in {year}"
        else:
            date_phrase = ""

        parts = [f"A {dtype.lower()}"]
        if subtype and not _is_na(subtype):
            parts.append(f"({subtype.lower()})")
        parts.append(f"in {country}")
        if date_phrase:
            parts.append(date_phrase)
        sentence = " ".join(parts) + "."

        details = []
        if not _is_na(deaths):
            details.append(f"Deaths: {_fmt_num(deaths)}.")
        else:
            details.append("Death toll: unknown.")

        if not _is_na(affected):
            details.append(f"Affected: {_fmt_num(affected)} people.")

        if not _is_na(damage):
            details.append(f"Economic damage: ${_fmt_num(damage)} USD.")

        if not _is_na(magnitude):
            details.append(f"Magnitude: {magnitude}.")

        return f"{sentence} {' '.join(details)}".strip()

    def chunk_record(self, record: dict[str, Any]) -> list[ChunkPair]:
        """Enrich + chunk a single record."""
        text = self.enrich_text(record)
        return self.chunk(
            text,
            metadata={
                "event_id": record.get("event_id"),
                "disaster_type": record.get("disaster_type"),
                "country": record.get("country"),
                "country_iso3": record.get("country_iso3"),
            },
        )

    # -------------------------------------------------------------------------
    # private helpers
    # -------------------------------------------------------------------------

    def _split(self, text: str, size: int, overlap: int) -> list[str]:
        """Split text into token-sized chunks with overlap."""
        tokens = text.split()
        if len(tokens) <= size:
            return [text]
        chunks = []
        start = 0
        while start < len(tokens):
            end = min(start + size, len(tokens))
            chunks.append(" ".join(tokens[start:end]))
            start += size - overlap
        return chunks

    @staticmethod
    def _token_count(text: str) -> int:
        return len(text.split())


def _is_na(val: Any) -> bool:
    if val is None:
        return True
    try:
        import pandas as pd

        return pd.isna(val)
    except (TypeError, ImportError):
        return False


def _extract_year(date_val: Any) -> str:
    s = str(date_val)
    m = re.search(r"\b(1[89]\d{2}|2\d{3})\b", s)
    return m.group(1) if m else s[:4]


def _fmt_num(val: Any) -> str:
    try:
        n = float(val)
        if n >= 1_000_000:
            return f"{n / 1_000_000:.1f}M"
        if n >= 1_000:
            return f"{n / 1_000:.1f}K"
        return str(int(n))
    except (TypeError, ValueError):
        return str(val)
