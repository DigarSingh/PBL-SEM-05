"""Demand forecasting from daily unit sales.

Model: weekly totals (complete weeks only) are fitted with a recency-weighted linear regression
(scikit-learn). The forecast for the horizon is the extrapolated weekly trend, pro-rated to days,
with an 80% prediction interval from the residual spread. Series without enough history are
reported as INSUFFICIENT_DATA rather than guessed. This is deliberately simple and explainable:
it captures trend but not holidays or promotions.
"""

from __future__ import annotations

import math
from datetime import UTC, date, datetime

import numpy as np
import pandas as pd
from sklearn.linear_model import LinearRegression

MODEL_NAME = "weighted-linear-trend (weekly)"
MIN_TOTAL_UNITS = 8
MIN_SELLING_DAYS = 6
MIN_HISTORY_DAYS = 28
MAX_WEEKS = 16
RECENCY_DECAY = 0.85
SAFETY_FACTOR = 1.2
Z_80 = 1.2816


def forecast_product(daily: dict[date, float], start: date, end: date, horizon_days: int, lead_time_days: int,
                     available: int) -> dict:
    index = pd.date_range(start, end, freq="D")
    series = pd.Series(0.0, index=index)
    for day, units in daily.items():
        stamp = pd.Timestamp(day)
        if stamp in series.index:
            series[stamp] += float(units)

    selling = series[series > 0]
    total = float(series.sum())
    if total < MIN_TOTAL_UNITS or len(selling) < MIN_SELLING_DAYS:
        reason = f"Only {int(total)} units on {len(selling)} days of sales in the history window"
        return _insufficient(available, reason)
    history_days = (series.index[-1] - selling.index[0]).days + 1
    if history_days < MIN_HISTORY_DAYS:
        return _insufficient(available, f"Sales history covers only {history_days} days (need {MIN_HISTORY_DAYS})")

    # Complete weeks ending at the last day, most recent last.
    trimmed = series[selling.index[0]:]
    weeks = len(trimmed) // 7
    weekly = trimmed.iloc[len(trimmed) - weeks * 7:].to_numpy().reshape(weeks, 7).sum(axis=1)[-MAX_WEEKS:]
    n = len(weekly)
    x = np.arange(n, dtype=float).reshape(-1, 1)
    sample_weight = RECENCY_DECAY ** (n - 1 - np.arange(n))
    model = LinearRegression().fit(x, weekly, sample_weight=sample_weight)
    slope = float(model.coef_[0])
    fitted = model.predict(x)
    residual_std = float(np.sqrt(np.average((weekly - fitted) ** 2, weights=sample_weight)))

    horizon_weeks = horizon_days / 7.0
    future_x = np.array([[n - 1 + (step + 1)] for step in range(math.ceil(horizon_weeks))], dtype=float)
    future = np.clip(model.predict(future_x), 0.0, None)
    predicted = float(future[: int(horizon_weeks)].sum() + (horizon_weeks % 1) * (future[-1] if len(future) else 0.0))
    spread = Z_80 * residual_std * math.sqrt(max(horizon_weeks, 1.0))
    lower, upper = max(0.0, predicted - spread), predicted + spread

    mean_week = float(weekly.mean()) if n else 0.0
    relative = slope / mean_week if mean_week > 0 else 0.0
    trend = "RISING" if relative > 0.05 else "FALLING" if relative < -0.05 else "STABLE"
    daily_rate = predicted / horizon_days if horizon_days else 0.0
    days_of_cover = round(available / daily_rate, 1) if daily_rate > 0 else None
    needed = daily_rate * (lead_time_days + horizon_days) * SAFETY_FACTOR
    restock = max(0, math.ceil(needed - available))

    backtest = _backtest_mae(weekly, sample_weight)
    note = None
    if days_of_cover is not None and days_of_cover < lead_time_days:
        note = "Expected to sell out before new stock could arrive"
    elif restock == 0:
        note = "Current stock covers the forecast horizon"
    return {
        "status": "OK",
        "predicted_units": round(predicted, 1),
        "lower": round(lower, 1),
        "upper": round(upper, 1),
        "daily_rate": round(daily_rate, 2),
        "trend": trend,
        "available": available,
        "days_of_cover": days_of_cover,
        "restock_quantity": restock,
        "weeks_of_history": n,
        "backtest_mae_weekly": backtest,
        "note": note,
    }


def _backtest_mae(weekly: np.ndarray, weights: np.ndarray) -> float | None:
    """Mean absolute error of predicting each of the last two weeks from the weeks before it."""
    n = len(weekly)
    if n < 8:
        return None
    errors = []
    for holdout in (n - 2, n - 1):
        x = np.arange(holdout, dtype=float).reshape(-1, 1)
        model = LinearRegression().fit(x, weekly[:holdout], sample_weight=weights[-holdout:])
        errors.append(abs(float(max(0.0, model.predict([[holdout]])[0])) - float(weekly[holdout])))
    return round(float(np.mean(errors)), 2)


def _insufficient(available: int, reason: str) -> dict:
    return {
        "status": "INSUFFICIENT_DATA",
        "predicted_units": None,
        "lower": None,
        "upper": None,
        "daily_rate": None,
        "trend": None,
        "available": available,
        "days_of_cover": None,
        "restock_quantity": None,
        "weeks_of_history": None,
        "backtest_mae_weekly": None,
        "note": f"Insufficient data: {reason}",
    }


def forecast(request: dict) -> dict:
    start = date.fromisoformat(request["history_start"])
    end = date.fromisoformat(request["history_end"])
    horizon = int(request.get("horizon_days", 14))
    lead_time = int(request.get("lead_time_days", 7))
    results = []
    for item in request.get("series", []):
        daily = {date.fromisoformat(point["date"]): float(point["units"]) for point in item.get("daily", [])}
        result = forecast_product(daily, start, end, horizon, lead_time, int(item.get("available", 0)))
        result["product_id"] = int(item["product_id"])
        result["name"] = item.get("name")
        results.append(result)
    return {
        "model": MODEL_NAME,
        "generated_at": datetime.now(UTC).isoformat(),
        "horizon_days": horizon,
        "lead_time_days": lead_time,
        "forecasts": results,
    }
