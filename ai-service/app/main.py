"""FastAPI application. All /v1 endpoints require the shared X-Internal-Api-Key header."""

from __future__ import annotations

import hmac
import logging
import os
from contextlib import asynccontextmanager

import numpy as np
from fastapi import Depends, FastAPI, Header, HTTPException, Response, status

from app import forecasting
from app.config import load_settings
from app.recommender import recommend_for_user, similar_products
from app.schemas import (
    ForecastRequest,
    RankRequest,
    RankResponse,
    RecommendationResponse,
    ScoredProduct,
    SimilarRequest,
    UserRecommendationRequest,
)
from app.state import ServiceState

logging.basicConfig(level=os.getenv("LOG_LEVEL", "INFO"), format="%(asctime)s %(levelname)s %(name)s: %(message)s")
log = logging.getLogger("shopsmart.ai")


def create_app(state: ServiceState | None = None, start_refresher: bool = True) -> FastAPI:
    service_state = state or ServiceState(load_settings())

    @asynccontextmanager
    async def lifespan(_: FastAPI):
        if start_refresher:
            service_state.start()
        yield
        service_state.stop()

    app = FastAPI(title="ShopSmart AI service", version="1.0.0", lifespan=lifespan,
                  docs_url="/docs", redoc_url=None)
    app.state.service = service_state

    def require_api_key(x_internal_api_key: str | None = Header(default=None)) -> None:
        expected = service_state.settings.api_key
        if not expected or not x_internal_api_key or not hmac.compare_digest(expected, x_internal_api_key):
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Invalid or missing API key")

    def ready_index():
        index, cf = service_state.snapshot()
        if index is None or len(index) == 0:
            raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                                detail="Product index is not ready yet")
        return index, cf

    @app.get("/health")
    def health(response: Response) -> dict:
        index, cf = service_state.snapshot()
        ready = index is not None
        if not ready:
            response.status_code = status.HTTP_503_SERVICE_UNAVAILABLE
        return {
            "status": "ok" if ready else "starting",
            "embedding_backend": index.backend.name if index is not None else None,
            "catalog_size": len(index) if index is not None else 0,
            "catalog_version": index.version if index is not None else None,
            "interactions": cf.interaction_count if cf is not None else 0,
            "last_refresh": service_state.last_refresh,
            "last_error": service_state.last_error,
        }

    @app.post("/v1/search/rank", response_model=RankResponse, dependencies=[Depends(require_api_key)])
    def rank(request: RankRequest) -> RankResponse:
        index, _ = ready_index()
        scores = index.query_scores([request.query])[0]
        rows = index.rows_for(request.candidate_ids) if request.candidate_ids else list(range(len(index)))
        ranked = sorted(rows, key=lambda row: -scores[row])[: request.limit]
        results = [ScoredProduct(product_id=index.products[row].id, score=round(float(scores[row]), 4))
                   for row in ranked if scores[row] > 0.0]
        return RankResponse(backend=index.backend.name, results=results)

    @app.post("/v1/recommendations/similar", response_model=RecommendationResponse,
              dependencies=[Depends(require_api_key)])
    def similar(request: SimilarRequest) -> RecommendationResponse:
        index, cf = ready_index()
        results = similar_products(index, cf, request.product_id, request.limit, request.exclude_ids)
        return RecommendationResponse(strategy="content+collaborative", results=[ScoredProduct(**r) for r in results])

    @app.post("/v1/recommendations/user", response_model=RecommendationResponse,
              dependencies=[Depends(require_api_key)])
    def for_user(request: UserRecommendationRequest) -> RecommendationResponse:
        index, cf = ready_index()
        results = recommend_for_user(index, cf, [e.model_dump() for e in request.events], request.queries,
                                     request.exclude_ids, request.limit)
        strategy = "hybrid" if request.events or request.queries else "popular"
        return RecommendationResponse(strategy=strategy, results=[ScoredProduct(**r) for r in results])

    @app.post("/v1/forecast/demand", dependencies=[Depends(require_api_key)])
    def forecast(request: ForecastRequest) -> dict:
        return forecasting.forecast(request.model_dump())

    @app.post("/v1/admin/refresh", status_code=status.HTTP_202_ACCEPTED, dependencies=[Depends(require_api_key)])
    def refresh() -> dict:
        service_state.request_refresh()
        return {"status": "refresh requested"}

    return app


def _numpy_safe() -> None:
    np.seterr(all="ignore")


_numpy_safe()
app = create_app()
