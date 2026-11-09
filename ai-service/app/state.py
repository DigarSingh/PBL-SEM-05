"""Holds the current index/models and refreshes them from the backend in the background."""

from __future__ import annotations

import logging
import threading
import time
from collections.abc import Callable
from datetime import UTC, datetime

from app.backend_client import BackendClient
from app.collaborative import ItemSimilarity
from app.config import Settings
from app.embeddings import EmbeddingBackend, create_backend
from app.index import ProductIndex

log = logging.getLogger(__name__)


class ServiceState:
    def __init__(self, settings: Settings, client: BackendClient | None = None,
                 backend_factory: Callable[[], EmbeddingBackend] | None = None) -> None:
        self.settings = settings
        self.client = client or BackendClient(settings)
        self._backend_factory = backend_factory or (
            lambda: create_backend(settings.embedding_backend, settings.embedding_model))
        self._backend: EmbeddingBackend | None = None
        self._lock = threading.Lock()
        self._wake = threading.Event()
        self._stop = threading.Event()
        self._thread: threading.Thread | None = None
        self.index: ProductIndex | None = None
        self.cf: ItemSimilarity | None = None
        self.last_refresh: str | None = None
        self.last_error: str | None = None

    # -- lifecycle ---------------------------------------------------------------------------
    def start(self) -> None:
        self._thread = threading.Thread(target=self._loop, name="catalog-refresh", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        self._wake.set()

    def request_refresh(self) -> None:
        self._wake.set()

    def _loop(self) -> None:
        delay = 2.0
        while not self._stop.is_set():
            try:
                self.refresh()
                delay = float(self.settings.refresh_interval_seconds)
            except Exception as error:  # backend not up yet, network errors...
                self.last_error = str(error)
                log.warning("Catalog refresh failed: %s", error)
                delay = min(max(delay * 2, 5.0), 60.0)
            self._wake.wait(timeout=delay)
            self._wake.clear()

    # -- refresh ------------------------------------------------------------------------------
    def backend(self) -> EmbeddingBackend:
        if self._backend is None:
            self._backend = self._backend_factory()
        return self._backend

    def refresh(self) -> None:
        catalog = self.client.catalog()
        version = catalog.get("version", "")
        current = self.index
        if current is None or current.version != version:
            started = time.perf_counter()
            index = ProductIndex(self.backend(), catalog.get("products", []), version)
            log.info("Indexed %d products with %s in %.2fs", len(index), index.backend.name,
                     time.perf_counter() - started)
        else:
            index = current
        interactions = self.client.interactions(self.settings.interaction_days).get("interactions", [])
        cf = ItemSimilarity(interactions, index.row, len(index))
        with self._lock:
            self.index, self.cf = index, cf
        self.last_refresh = datetime.now(UTC).isoformat()
        self.last_error = None

    def load(self, products: list[dict], interactions: list[dict], version: str = "test") -> None:
        """Loads data directly (tests, offline use)."""
        index = ProductIndex(self.backend(), products, version)
        cf = ItemSimilarity(interactions, index.row, len(index))
        with self._lock:
            self.index, self.cf = index, cf
        self.last_refresh = datetime.now(UTC).isoformat()

    def snapshot(self) -> tuple[ProductIndex | None, ItemSimilarity | None]:
        with self._lock:
            return self.index, self.cf
