"""Item-item collaborative filtering from aggregated (user, product, weight) interactions."""

from __future__ import annotations

import numpy as np
from scipy import sparse
from sklearn.preprocessing import normalize


class ItemSimilarity:
    """Cosine similarity between products based on which users interacted with both."""

    def __init__(self, interactions: list[dict], product_row: dict[int, int], n_products: int) -> None:
        self.n_products = n_products
        users: dict[int, int] = {}
        rows, cols, values = [], [], []
        for item in interactions:
            product = int(item["product_id"])
            if product not in product_row:
                continue
            user = users.setdefault(int(item["user_id"]), len(users))
            rows.append(user)
            cols.append(product_row[product])
            values.append(np.log1p(float(item["weight"])))
        self.user_count = len(users)
        self.interaction_count = len(values)
        if not values:
            self.similarity = sparse.csr_matrix((n_products, n_products))
            return
        matrix = sparse.csr_matrix((values, (rows, cols)), shape=(len(users), n_products))
        items = normalize(matrix.T.tocsr())
        similarity = (items @ items.T).tolil()
        similarity.setdiag(0)
        self.similarity = similarity.tocsr()

    @property
    def available(self) -> bool:
        return self.interaction_count > 0

    def scores(self, weighted_rows: dict[int, float]) -> np.ndarray:
        """Weighted sum of similarities to the given products, scaled to 0..1."""
        if not self.available or not weighted_rows:
            return np.zeros(self.n_products)
        vector = np.zeros(self.n_products)
        for row, weight in weighted_rows.items():
            vector[row] = weight
        scores = np.asarray(self.similarity.T @ vector).ravel()
        peak = scores.max()
        return scores / peak if peak > 0 else scores

    def related(self, row: int) -> np.ndarray:
        if not self.available:
            return np.zeros(self.n_products)
        return np.asarray(self.similarity[row].toarray()).ravel()
