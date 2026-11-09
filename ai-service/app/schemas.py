"""Request/response models of the internal AI API."""

from __future__ import annotations

from datetime import date

from pydantic import BaseModel, Field, model_validator


class RankRequest(BaseModel):
    query: str = Field(min_length=1, max_length=300)
    candidate_ids: list[int] = Field(default_factory=list, max_length=2000)
    limit: int = Field(default=50, ge=1, le=2000)


class ScoredProduct(BaseModel):
    product_id: int
    score: float
    reason: str | None = None


class RankResponse(BaseModel):
    backend: str
    results: list[ScoredProduct]


class SimilarRequest(BaseModel):
    product_id: int
    limit: int = Field(default=8, ge=1, le=100)
    exclude_ids: list[int] = Field(default_factory=list)


class Event(BaseModel):
    product_id: int
    event_type: str
    weight: float | None = None
    occurred_at: str | None = None


class UserRecommendationRequest(BaseModel):
    events: list[Event] = Field(default_factory=list, max_length=1000)
    queries: list[str] = Field(default_factory=list, max_length=50)
    exclude_ids: list[int] = Field(default_factory=list)
    limit: int = Field(default=12, ge=1, le=100)


class RecommendationResponse(BaseModel):
    strategy: str
    results: list[ScoredProduct]


class DailyPoint(BaseModel):
    date: str
    units: float = Field(ge=0)


class ProductSeries(BaseModel):
    product_id: int
    name: str | None = None
    available: int = 0
    daily: list[DailyPoint] = Field(default_factory=list, max_length=180)


class ForecastRequest(BaseModel):
    history_start: str
    history_end: str
    horizon_days: int = Field(default=14, ge=1, le=90)
    lead_time_days: int = Field(default=7, ge=0, le=90)
    series: list[ProductSeries] = Field(default_factory=list, max_length=100)

    @model_validator(mode="after")
    def validate_history_window(self) -> ForecastRequest:
        try:
            start = date.fromisoformat(self.history_start)
            end = date.fromisoformat(self.history_end)
        except ValueError as error:
            raise ValueError("history_start and history_end must be ISO dates") from error
        if start > end:
            raise ValueError("history_start must be on or before history_end")
        if (end - start).days > 730:
            raise ValueError("history window cannot exceed 731 days")
        return self
