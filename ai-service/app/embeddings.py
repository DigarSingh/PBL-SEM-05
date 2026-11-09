"""Text embedding backends.

``sentence-transformers`` (MiniLM) gives genuinely semantic matching ("headset" ~ "headphones",
"for esports" ~ "gaming"). When it is unavailable (not installed, no network to fetch the model)
the service falls back to a TF-IDF model over words and character n-grams, which is lexical but
robust to plurals and partial words. Both produce L2-normalised vectors so cosine similarity is a
dot product; ``calibrate`` maps each backend's typical similarity range onto 0..1.
"""

from __future__ import annotations

import logging
from typing import Protocol

import numpy as np
from scipy import sparse
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.pipeline import FeatureUnion
from sklearn.preprocessing import normalize

log = logging.getLogger(__name__)


class EmbeddingBackend(Protocol):
    name: str

    def fit(self, documents: list[str]) -> None: ...

    def encode(self, texts: list[str]): ...

    def calibrate(self, similarities: np.ndarray) -> np.ndarray: ...


class SentenceTransformerBackend:
    name = "sentence-transformers"

    def __init__(self, model_name: str) -> None:
        from sentence_transformers import SentenceTransformer  # heavy import, deferred

        self.model_name = model_name
        self._model = SentenceTransformer(model_name, device="cpu")

    def fit(self, documents: list[str]) -> None:
        """Pre-trained model: nothing to fit."""

    def encode(self, texts: list[str]) -> np.ndarray:
        vectors = self._model.encode(texts, batch_size=64, normalize_embeddings=True, show_progress_bar=False)
        return np.asarray(vectors, dtype=np.float32)

    def calibrate(self, similarities: np.ndarray) -> np.ndarray:
        return np.clip((similarities - 0.15) / 0.45, 0.0, 1.0)


class TfidfBackend:
    name = "tfidf"

    def __init__(self) -> None:
        self._vectorizer: FeatureUnion | None = None

    def fit(self, documents: list[str]) -> None:
        self._vectorizer = FeatureUnion(
            [
                ("words", TfidfVectorizer(ngram_range=(1, 2), sublinear_tf=True, stop_words="english", min_df=1)),
                ("chars", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), sublinear_tf=True, min_df=1)),
            ]
        )
        self._vectorizer.fit(documents or ["empty"])

    def encode(self, texts: list[str]) -> sparse.csr_matrix:
        if self._vectorizer is None:
            raise RuntimeError("TF-IDF backend used before fit()")
        return normalize(self._vectorizer.transform(texts)).tocsr()

    def calibrate(self, similarities: np.ndarray) -> np.ndarray:
        return np.clip((similarities - 0.03) / 0.32, 0.0, 1.0)


def create_backend(preferred: str, model_name: str) -> EmbeddingBackend:
    """Returns the preferred backend, falling back to TF-IDF unless sentence-transformers was required."""
    if preferred in ("auto", "sentence-transformers"):
        try:
            backend = SentenceTransformerBackend(model_name)
            log.info("Embedding backend: sentence-transformers (%s)", model_name)
            return backend
        except Exception as error:  # ImportError, network errors while downloading the model, ...
            if preferred == "sentence-transformers":
                raise
            log.warning("sentence-transformers unavailable (%s); using TF-IDF embeddings", error)
    log.info("Embedding backend: tfidf")
    return TfidfBackend()


def similarity(matrix, vectors) -> np.ndarray:
    """Cosine similarities between the rows of ``matrix`` and each row of ``vectors`` (both L2-normalised)."""
    result = matrix @ vectors.T
    if sparse.issparse(result):
        result = result.toarray()
    return np.asarray(result, dtype=np.float64)
