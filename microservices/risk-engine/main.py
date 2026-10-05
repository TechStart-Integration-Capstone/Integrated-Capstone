"""
PayPink 2.0 — Risk Engine
FastAPI microservice. Scores transfer requests for fraud risk.

Endpoints:
  POST /score   — score a transfer request, returns 0.00-1.00
  GET  /health  — Docker health check compatible

OTel: W3C traceparent header extracted and propagated so traces
appear in the same Jaeger/Tempo trace as the gateway request.
Manual tracing only (no auto-instrumentation) for Python 3.12 compatibility.
"""

from __future__ import annotations

import os
import time
import logging

from fastapi import FastAPI, Request
from pydantic import BaseModel, Field
from typing import Optional, List

from scorer import ScoreRequest, score

# ── OpenTelemetry setup (manual — no pkg_resources dependency) ──────────────
from opentelemetry import trace
from opentelemetry.sdk.trace import TracerProvider
from opentelemetry.sdk.trace.export import BatchSpanProcessor
from opentelemetry.exporter.otlp.proto.grpc.trace_exporter import OTLPSpanExporter
from opentelemetry.sdk.resources import Resource
from opentelemetry.trace.propagation.tracecontext import TraceContextTextMapPropagator
from opentelemetry.propagators.textmap import DefaultGetter

OTEL_ENDPOINT = os.getenv("OTEL_EXPORTER_OTLP_ENDPOINT", "http://otel-collector:4317")
SERVICE_NAME  = os.getenv("OTEL_SERVICE_NAME", "risk-engine")

resource = Resource.create({"service.name": SERVICE_NAME})
provider = TracerProvider(resource=resource)

try:
    exporter = OTLPSpanExporter(endpoint=OTEL_ENDPOINT, insecure=True)
    provider.add_span_processor(BatchSpanProcessor(exporter))
except Exception:
    pass  # OTel is best-effort — never block startup

trace.set_tracer_provider(provider)
tracer = trace.get_tracer(SERVICE_NAME)
propagator = TraceContextTextMapPropagator()

# ── Logging ──────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s","level":"%(levelname)s","service":"risk-engine","msg":"%(message)s"}'
)
log = logging.getLogger("risk-engine")

# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="PayPink Risk Engine",
    description="Rule-based fraud scoring for PayPink 2.0 remittance transfers.",
    version="2.0.0",
)


# ── Request / Response models ─────────────────────────────────────────────────
class ScoreRequestBody(BaseModel):
    accountId: int                   = Field(..., gt=0)
    customerId: int                  = Field(..., gt=0)
    amount: float                    = Field(..., gt=0)
    currency: str                    = Field(default="PHP", max_length=10)
    transactionType: str             = Field(..., max_length=50)
    targetAccountId: Optional[int]   = Field(default=None)
    accountAgeHours: Optional[float] = Field(default=None, ge=0)
    recentTxCount: Optional[int]     = Field(default=None, ge=0)

    model_config = {"json_schema_extra": {"example": {
        "accountId": 1,
        "customerId": 1,
        "amount": 75000.00,
        "currency": "PHP",
        "transactionType": "TRANSFER_OUT",
        "targetAccountId": 2,
        "accountAgeHours": 720.0,
        "recentTxCount": 1
    }}}


class ScoreResponseBody(BaseModel):
    score: float
    decision: str
    reasons: List[str]
    latencyMs: float
    service: str = "risk-engine"


# ── Endpoints ─────────────────────────────────────────────────────────────────
@app.post("/score", response_model=ScoreResponseBody, status_code=200)
async def score_transfer(body: ScoreRequestBody, request: Request):
    """
    Score a transfer request.
    Returns score 0.00-1.00. decision = APPROVE | REJECT.
    Score > 0.85 → REJECT per AGENTS.md: no risk score → money never moves.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")

    # Extract W3C traceparent from incoming request and propagate context
    carrier = dict(request.headers)
    ctx = propagator.extract(carrier=carrier, getter=DefaultGetter())

    start = time.perf_counter()

    with tracer.start_as_current_span("risk.score", context=ctx) as span:
        span.set_attribute("accountId", body.accountId)
        span.set_attribute("amount", body.amount)
        span.set_attribute("currency", body.currency)
        span.set_attribute("correlationId", correlation_id)

        req = ScoreRequest(
            accountId=body.accountId,
            customerId=body.customerId,
            amount=body.amount,
            currency=body.currency,
            transactionType=body.transactionType,
            targetAccountId=body.targetAccountId,
            accountAgeHours=body.accountAgeHours,
            recentTxCount=body.recentTxCount,
        )

        result = score(req)
        latency_ms = round((time.perf_counter() - start) * 1000, 2)

        span.set_attribute("risk.score", result.score)
        span.set_attribute("risk.decision", result.decision)

        log.info(
            f"score={result.score} decision={result.decision} "
            f"amount={body.amount} accountId={body.accountId} "
            f"reasons={result.reasons} latencyMs={latency_ms} "
            f"correlationId={correlation_id}"
        )

        return ScoreResponseBody(
            score=result.score,
            decision=result.decision,
            reasons=result.reasons,
            latencyMs=latency_ms,
        )


@app.get("/health")
async def health():
    """Docker health check + actuator-compatible response."""
    return {"status": "UP", "service": "risk-engine", "version": "2.0.0"}


@app.get("/")
async def root():
    return {"service": "risk-engine", "docs": "/docs"}
