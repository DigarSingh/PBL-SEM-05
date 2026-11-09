"""Pulls catalog and interaction data from the backend's internal API (never from the database)."""

from __future__ import annotations

import httpx

from app.config import Settings


class BackendClient:
    def __init__(self, settings: Settings) -> None:
        self._settings = settings

    def _get(self, path: str, params: dict | None = None) -> dict:
        headers = {"X-Internal-Api-Key": self._settings.api_key}
        timeout = self._settings.backend_timeout_seconds
        with httpx.Client(base_url=self._settings.backend_url, timeout=timeout) as client:
            response = client.get(path, headers=headers, params=params)
            response.raise_for_status()
            return response.json()

    def catalog(self) -> dict:
        return self._get("/internal/ai/catalog")

    def interactions(self, days: int) -> dict:
        return self._get("/internal/ai/interactions", {"days": days})
