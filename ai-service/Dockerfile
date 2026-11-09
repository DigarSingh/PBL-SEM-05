# syntax=docker/dockerfile:1.7
FROM python:3.14-slim

ENV PYTHONDONTWRITEBYTECODE=1 \
    PYTHONUNBUFFERED=1 \
    PIP_NO_CACHE_DIR=1 \
    PIP_DISABLE_PIP_VERSION_CHECK=1 \
    HF_HOME=/opt/models

# WITH_EMBEDDINGS=true installs CPU-only PyTorch + sentence-transformers and bakes the MiniLM model
# into the image, so the container never downloads at runtime. false = smaller image with TF-IDF.
ARG WITH_EMBEDDINGS=true
ARG EMBEDDING_MODEL=sentence-transformers/all-MiniLM-L6-v2

WORKDIR /app
COPY requirements.txt requirements-ml.txt ./
RUN pip install -r requirements.txt \
 && mkdir -p /opt/models \
 && if [ "$WITH_EMBEDDINGS" = "true" ]; then \
      pip install --index-url https://download.pytorch.org/whl/cpu torch \
      && pip install -r requirements-ml.txt \
      && python -c "from sentence_transformers import SentenceTransformer; SentenceTransformer('$EMBEDDING_MODEL')"; \
    fi

COPY app ./app
RUN useradd --system --uid 10001 --home /app app && chown -R app /app /opt/models
USER app

ENV EMBEDDING_BACKEND=auto \
    EMBEDDING_MODEL=${EMBEDDING_MODEL} \
    HF_HUB_OFFLINE=1
EXPOSE 8000
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=5 \
  CMD python -c "import sys, urllib.request; sys.exit(0 if urllib.request.urlopen('http://127.0.0.1:8000/health', timeout=3).status == 200 else 1)"
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--proxy-headers"]
