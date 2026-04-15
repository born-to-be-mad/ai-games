"""Pydantic validation models for all MCP tool parameters."""

from typing import Optional

from pydantic import BaseModel, Field, field_validator

VALID_DISASTER_TYPES = {
    "flood",
    "earthquake",
    "storm",
    "drought",
    "wildfire",
    "volcano",
    "landslide",
    "epidemic",
    "extreme_temperature",
}


class DisasterQueryParams(BaseModel):
    disaster_type: Optional[str] = Field(None, max_length=50)
    country: Optional[str] = Field(None, max_length=100)
    year_from: Optional[int] = Field(None, ge=1900, le=2030)
    year_to: Optional[int] = Field(None, ge=1900, le=2030)
    limit: int = Field(20, ge=1, le=100)

    @field_validator("disaster_type")
    @classmethod
    def validate_disaster_type(cls, v: str | None) -> str | None:
        if v is None:
            return None
        normalized = v.strip().lower()
        if normalized not in VALID_DISASTER_TYPES:
            raise ValueError(
                f"Unknown disaster type: '{v}'. Valid types: {sorted(VALID_DISASTER_TYPES)}"
            )
        return normalized

    @field_validator("country")
    @classmethod
    def validate_country(cls, v: str | None) -> str | None:
        if v is None:
            return None
        stripped = v.strip()
        if len(stripped) < 2:
            raise ValueError("country must be at least 2 characters")
        return stripped

    model_config = {"str_strip_whitespace": True}


class DisasterStatsParams(BaseModel):
    disaster_type: Optional[str] = Field(None, max_length=50)
    country: Optional[str] = Field(None, max_length=100)
    year_from: Optional[int] = Field(None, ge=1900, le=2030)
    year_to: Optional[int] = Field(None, ge=1900, le=2030)

    @field_validator("disaster_type")
    @classmethod
    def validate_disaster_type(cls, v: str | None) -> str | None:
        if v is None:
            return None
        normalized = v.strip().lower()
        if normalized not in VALID_DISASTER_TYPES:
            raise ValueError(
                f"Unknown disaster type: '{v}'. Valid types: {sorted(VALID_DISASTER_TYPES)}"
            )
        return normalized

    model_config = {"str_strip_whitespace": True}


class TrendParams(BaseModel):
    disaster_type: str = Field(..., max_length=50)
    country: Optional[str] = Field(None, max_length=100)
    year_from: int = Field(1970, ge=1900, le=2030)
    year_to: int = Field(2021, ge=1900, le=2030)

    @field_validator("disaster_type")
    @classmethod
    def validate_disaster_type(cls, v: str) -> str:
        normalized = v.strip().lower()
        if normalized not in VALID_DISASTER_TYPES:
            raise ValueError(
                f"Unknown disaster type: '{v}'. Valid types: {sorted(VALID_DISASTER_TYPES)}"
            )
        return normalized

    model_config = {"str_strip_whitespace": True}


class CompareCountriesParams(BaseModel):
    disaster_type: str = Field(..., max_length=50)
    countries: list[str] = Field(..., min_length=2, max_length=5)
    year_from: Optional[int] = Field(None, ge=1900, le=2030)
    year_to: Optional[int] = Field(None, ge=1900, le=2030)

    @field_validator("disaster_type")
    @classmethod
    def validate_disaster_type(cls, v: str) -> str:
        normalized = v.strip().lower()
        if normalized not in VALID_DISASTER_TYPES:
            raise ValueError(
                f"Unknown disaster type: '{v}'. Valid types: {sorted(VALID_DISASTER_TYPES)}"
            )
        return normalized

    model_config = {"str_strip_whitespace": True}
