"""In-memory product index: one embedding per sellable product plus popularity signals."""

from __future__ import annotations

from dataclasses import dataclass

import numpy as np

from app.embeddings import EmbeddingBackend, similarity


@dataclass(frozen=True)
class IndexedProduct:
    id: int
    name: str
    brand: str
    category: str
    category_id: int
    brand_id: int
    price: float
    rating: float
    review_count: int
    sold_count: int
    available: int


def product_document(product: dict) -> str:
    """The text that represents a product for embedding (name and category carry the most signal)."""
    parts = [
        product.get("name", ""),
        f"{product.get('brand', '')} {product.get('category', '')}",
        product.get("short_description", ""),
        " ".join(product.get("tags", [])),
        "; ".join(product.get("specifications", [])),
        product.get("description", ""),
    ]
    return ". ".join(part for part in parts if part)


class ProductIndex:
    def __init__(self, backend: EmbeddingBackend, products: list[dict], version: str) -> None:
        self.backend = backend
        self.version = version
        self.products = [
            IndexedProduct(
                id=int(p["id"]),
                name=p.get("name", ""),
                brand=p.get("brand", ""),
                category=p.get("category", ""),
                category_id=int(p.get("category_id", 0)),
                brand_id=int(p.get("brand_id", 0)),
                price=float(p.get("price", 0.0)),
                rating=float(p.get("rating", 0.0)),
                review_count=int(p.get("review_count", 0)),
                sold_count=int(p.get("sold_count", 0)),
                available=int(p.get("available", 0)),
            )
            for p in products
        ]
        self.row = {product.id: index for index, product in enumerate(self.products)}
        documents = [product_document(p) for p in products]
        backend.fit(documents)
        self.vectors = backend.encode(documents) if documents else None
        self.category_ids = np.array([p.category_id for p in self.products], dtype=np.int64)
        self.brand_ids = np.array([p.brand_id for p in self.products], dtype=np.int64)
        self.in_stock = np.array([p.available > 0 for p in self.products], dtype=bool)
        sold = np.array([p.sold_count for p in self.products], dtype=np.float64)
        rating = np.array([p.rating for p in self.products], dtype=np.float64)
        reviews = np.array([p.review_count for p in self.products], dtype=np.float64)
        # Popularity prior: log-scaled sales blended with a Bayesian-smoothed rating.
        sales = np.log1p(sold) / (np.log1p(sold.max()) if len(sold) and sold.max() > 0 else 1.0)
        smoothed = (rating * reviews + 4.0 * 5) / (reviews + 5)
        self.popularity = 0.7 * sales + 0.3 * (smoothed / 5.0) if len(sold) else sales

    def __len__(self) -> int:
        return len(self.products)

    def rows_for(self, ids: list[int]) -> list[int]:
        return [self.row[i] for i in ids if i in self.row]

    def query_scores(self, texts: list[str]) -> np.ndarray:
        """Calibrated 0..1 relevance of every product to each text, shape (len(texts), n_products)."""
        if self.vectors is None or not texts:
            return np.zeros((len(texts), len(self.products)))
        raw = similarity(self.vectors, self.backend.encode(texts)).T
        return self.backend.calibrate(raw)

    def product_scores(self, product_rows: list[int]) -> np.ndarray:
        """Calibrated similarity of every product to each given product, shape (len(rows), n_products)."""
        if self.vectors is None or not product_rows:
            return np.zeros((len(product_rows), len(self.products)))
        raw = similarity(self.vectors, self.vectors[product_rows]).T
        return self.backend.calibrate(raw)
