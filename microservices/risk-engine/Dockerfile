# ── PayPink 2.0 Risk Engine ──────────────────────────────────────────────────
# Python 3.11 slim — cached locally, compatible with all dependencies
FROM python:3.11-slim

WORKDIR /app

# Install dependencies first (layer cache)
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source
COPY scorer.py .
COPY ml_scorer.py .
COPY main.py .

# Expose internal port (no host binding — accessed via ledger-net only)
EXPOSE 8000

# Health check — matches /health endpoint
HEALTHCHECK --interval=15s --timeout=5s --start-period=20s --retries=5 \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')" || exit 1

# Run with uvicorn
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000", "--log-level", "info"]
