"""Configuration from environment variables (see the repository's .env.example)."""

import os
from dataclasses import dataclass


@dataclass(frozen=True)
class Settings:
    backend_url: str
    api_key: str
    embedding_backend: str
    embedding_model: str
    refresh_interval_seconds: int
    interaction_days: int
    backend_timeout_seconds: float


def load_settings() -> Settings:
    return Settings(
        backend_url=os.getenv("BACKEND_URL", "http://localhost:8080").rstrip("/"),
        api_key=os.getenv("AI_SERVICE_API_KEY", ""),
        embedding_backend=os.getenv("EMBEDDING_BACKEND", "auto").strip().lower(),
        embedding_model=os.getenv("EMBEDDING_MODEL", "sentence-transformers/all-MiniLM-L6-v2"),
        refresh_interval_seconds=int(os.getenv("REFRESH_INTERVAL_SECONDS", "120")),
        interaction_days=int(os.getenv("INTERACTION_DAYS", "180")),
        backend_timeout_seconds=float(os.getenv("BACKEND_TIMEOUT_SECONDS", "10")),
    )
