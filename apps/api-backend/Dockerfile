FROM python:3.12-slim

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PIP_NO_CACHE_DIR=1 \
    PATH="/app/.venv/bin:$PATH"

WORKDIR /app

RUN pip install --no-cache-dir uv

COPY pyproject.toml uv.lock ./
RUN uv sync --frozen --no-dev

COPY src ./src
COPY migrations ./migrations

EXPOSE 8000

# Run uvicorn from the venv directly. Avoid `uv run` here — it re-syncs deps
# (including dev) at every container start, adding ~60s of cold-start latency
# that makes the ALB health check flap.
CMD ["uvicorn", "src.main:app", "--host", "0.0.0.0", "--port", "8000"]
