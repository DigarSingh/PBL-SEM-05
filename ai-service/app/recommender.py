"""Hybrid recommendations: content similarity + collaborative filtering + popularity."""

from __future__ import annotations

import math
from datetime import UTC, datetime

import numpy as np

from app.collaborative import ItemSimilarity
from app.index import ProductIndex

EVENT_WEIGHTS = {
    "PURCHASE": 5.0,
    "ADD_TO_CART": 3.0,
    "WISHLIST_ADD": 3.0,
    "REVIEW": 2.0,
    "PRODUCT_VIEW": 1.0,
    "SEARCH": 1.0,
    "CATEGORY_VIEW": 0.5,
    "REMOVE_FROM_CART": -1.0,
    "WISHLIST_REMOVE": -1.5,
}
HALF_LIFE_DAYS = 14.0
# Short-term intent: what the shopper engaged with in the last two days is weighed on its own, so
# fresh browsing changes recommendations right away instead of being averaged into months of history.
RECENT_DAYS = 2.0


def _decay(occurred_at: str | None, now: datetime) -> float:
    if not occurred_at:
        return 1.0
    try:
        moment = datetime.fromisoformat(occurred_at.replace("Z", "+00:00"))
    except ValueError:
        return 1.0
    age_days = max(0.0, (now - moment).total_seconds() / 86400)
    return math.pow(0.5, age_days / HALF_LIFE_DAYS)


def _age_days(occurred_at: str | None, now: datetime) -> float | None:
    if not occurred_at:
        return None
    try:
        moment = datetime.fromisoformat(occurred_at.replace("Z", "+00:00"))
    except ValueError:
        return None
    return max(0.0, (now - moment).total_seconds() / 86400)


def _content(index: ProductIndex, positive: dict[int, float]) -> tuple[np.ndarray, np.ndarray | None]:
    """Weighted similarity of every product to the given engaged products, plus the closest source per product."""
    if not positive:
        return np.zeros(len(index)), None
    rows = list(positive)
    similarity = index.product_scores(rows)
    w = np.array([positive[r] for r in rows])
    return (similarity * w[:, None]).sum(axis=0) / w.sum(), np.array(rows)[similarity.argmax(axis=0)]


def _eligible(index: ProductIndex, exclude: set[int]) -> np.ndarray:
    mask = index.in_stock.copy()
    for product_id in exclude:
        row = index.row.get(product_id)
        if row is not None:
            mask[row] = False
    return mask


def recommend_for_user(index: ProductIndex, cf: ItemSimilarity | None, events: list[dict], queries: list[str],
                       exclude_ids: list[int], limit: int, now: datetime | None = None) -> list[dict]:
    now = now or datetime.now(UTC)
    weights: dict[int, float] = {}
    recent_weights: dict[int, float] = {}
    for event in events:
        row = index.row.get(int(event["product_id"]))
        if row is None:
            continue
        base = event.get("weight")
        base = float(base) if base is not None else EVENT_WEIGHTS.get(event.get("event_type", ""), 1.0)
        weights[row] = weights.get(row, 0.0) + base * _decay(event.get("occurred_at"), now)
        age = _age_days(event.get("occurred_at"), now)
        if age is not None and age <= RECENT_DAYS:
            recent_weights[row] = recent_weights.get(row, 0.0) + base
    positive = {row: w for row, w in weights.items() if w > 0}
    recent_positive = {row: w for row, w in recent_weights.items() if w > 0}

    content, reasons_from = _content(index, positive)
    recent, recent_from = _content(index, recent_positive)
    query_scores = index.query_scores(queries[:5]) if queries else np.zeros((0, len(index)))
    query_signal = query_scores.max(axis=0) if len(query_scores) else np.zeros(len(index))

    collaborative = cf.scores(positive) if cf is not None else np.zeros(len(index))
    has_cf = cf is not None and cf.available and collaborative.max() > 0
    if not positive and not len(query_scores):
        combined = index.popularity.copy()
    elif recent_positive and has_cf:
        combined = 0.25 * content + 0.3 * recent + 0.15 * query_signal + 0.2 * collaborative + 0.1 * index.popularity
    elif recent_positive:
        combined = 0.3 * content + 0.35 * recent + 0.2 * query_signal + 0.15 * index.popularity
    elif has_cf:
        combined = 0.45 * content + 0.2 * query_signal + 0.25 * collaborative + 0.1 * index.popularity
    else:
        combined = 0.6 * content + 0.25 * query_signal + 0.15 * index.popularity

    mask = _eligible(index, set(exclude_ids))
    for row in positive:  # do not recommend what the user is already engaging with
        mask[row] = False
    order = [row for row in np.argsort(-combined) if mask[row]][:limit]
    results = []
    for row in order:
        reason = "Popular with ShopSmart shoppers"
        if recent_from is not None and recent[row] > 0.2 and recent[row] >= max(content[row], query_signal[row]):
            reason = f"Because you recently viewed {index.products[int(recent_from[row])].name}"
        elif has_cf and collaborative[row] >= 0.5 and collaborative[row] >= content[row]:
            reason = "Customers with similar taste bought this"
        elif reasons_from is not None and content[row] > 0.2 and content[row] >= query_signal[row]:
            reason = f"Because you viewed {index.products[int(reasons_from[row])].name}"
        elif len(query_scores) and query_signal[row] > 0.2:
            best = int(query_scores[:, row].argmax())
            reason = f"Matches your search for \"{queries[best]}\""
        elif positive:
            reason = f"Popular in {index.products[row].category}"
        results.append({"product_id": index.products[row].id, "score": round(float(combined[row]), 4),
                        "reason": reason})
    return results


def similar_products(index: ProductIndex, cf: ItemSimilarity | None, product_id: int, limit: int,
                     exclude_ids: list[int]) -> list[dict]:
    row = index.row.get(product_id)
    if row is None:
        return []
    content = index.product_scores([row])[0]
    same_category = (index.category_ids == index.category_ids[row]).astype(float)
    same_brand = (index.brand_ids == index.brand_ids[row]).astype(float)
    related = cf.related(row) if cf is not None else np.zeros(len(index))
    peak = related.max()
    related = related / peak if peak > 0 else related
    combined = 0.6 * content + 0.15 * same_category + 0.05 * same_brand + 0.2 * related
    mask = _eligible(index, set(exclude_ids) | {product_id})
    order = [r for r in np.argsort(-combined) if mask[r]][:limit]
    name = index.products[row].name
    results = []
    for r in order:
        if related[r] >= 0.5 and related[r] * 0.2 >= content[r] * 0.6:
            reason = "Frequently bought by people who viewed this"
        elif same_category[r]:
            reason = f"Similar to {name}"
        else:
            reason = "You might also like"
        results.append({"product_id": index.products[r].id, "score": round(float(combined[r]), 4), "reason": reason})
    return results
