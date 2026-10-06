"""
PayPink 2.0 — Risk Engine
=========================
FastAPI microservice. Scores transfer requests for fraud risk.

Endpoints
---------
  POST /score   — combined rule + ML anomaly score, returns 0.00–1.00
  GET  /health  — Docker health check compatible
  GET  /        — service root / docs link

Scoring architecture (two-layer)
---------------------------------
  Layer 1 — Rule scorer  (scorer.py)
      Deterministic, human-auditable rules.
      Fires on amount thresholds, account age, velocity,
      amount-vs-average ratio, and currency mismatch.

  Layer 2 — ML scorer  (ml_scorer.py)
      Isolation Forest anomaly detector trained on synthetic
      Philippine retail banking transaction data.
      Catches patterns the rules can't express (e.g. off-hours
      + high ratio + new account all together).

  Combined score = max(rule_score, ml_score * ML_WEIGHT)
  Decision       = REJECT if combined > 0.85 else APPROVE
  Reasons        = union of rule reasons + ML anomaly reasons

  The ML score is down-weighted (ML_WEIGHT = 0.90 by default) so
  a borderline anomaly alone cannot reject a transfer — it must
  combine with at least one rule signal to cross the threshold.

OTel
----
  W3C traceparent header extracted and propagated so traces appear
  in the same Jaeger/Tempo trace as the upstream gateway request.
  Manual tracing only (no auto-instrumentation) for Python 3.12.
"""

from __future__ import annotations

import os
import time
import logging
from contextlib import asynccontextmanager
from typing import List, Optional

from fastapi import FastAPI, Request
from pydantic import BaseModel, Field

from scorer   import ScoreRequest, score
from ml_scorer import MLScoreRequest, score_ml, warm_up, ML_WEIGHT

# ── OpenTelemetry setup ───────────────────────────────────────────────────────
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
tracer     = trace.get_tracer(SERVICE_NAME)
propagator = TraceContextTextMapPropagator()

# ── Logging ───────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format='{"time":"%(asctime)s","level":"%(levelname)s","service":"risk-engine","msg":"%(message)s"}'
)
log = logging.getLogger("risk-engine")


# ── Application lifespan — warm up ML model before first request ──────────────
@asynccontextmanager
async def lifespan(app: FastAPI):
    log.info("Risk engine starting — warming up ML scorer…")
    warm_up()          # trains or loads the Isolation Forest from disk
    log.info("Risk engine ready (rule scorer + ML scorer)")
    yield
    log.info("Risk engine shutting down")


# ── FastAPI app ───────────────────────────────────────────────────────────────
app = FastAPI(
    title="PayPink Risk Engine",
    description=(
        "Two-layer fraud scoring for PayPink 2.0 remittance transfers. "
        "Layer 1: deterministic rules. Layer 2: Isolation Forest anomaly detection."
    ),
    version="3.0.0",
    lifespan=lifespan,
)


# ── Request / Response models ─────────────────────────────────────────────────
class ScoreRequestBody(BaseModel):
    accountId:          int            = Field(..., gt=0)
    customerId:         int            = Field(..., gt=0)
    amount:             float          = Field(..., gt=0)
    currency:           str            = Field(default="PHP", max_length=10)
    transactionType:    str            = Field(..., max_length=50)
    targetAccountId:    Optional[int]  = Field(default=None)
    # Enrichment fields populated by RiskEngineClient.java (Layer 1)
    accountAgeHours:    Optional[float] = Field(default=None, ge=0,
        description="Hours since source account was created")
    recentTxCount:      Optional[int]   = Field(default=None, ge=0,
        description="Transfers sent from this account in the last hour")
    amountVsAvgRatio:   Optional[float] = Field(default=None, ge=0,
        description="This amount divided by the account's 30-day average transfer amount")

    model_config = {"json_schema_extra": {"example": {
        "accountId":        1,
        "customerId":       1,
        "amount":           75000.00,
        "currency":         "PHP",
        "transactionType":  "P2P_REMITTANCE",
        "targetAccountId":  2,
        "accountAgeHours":  720.0,
        "recentTxCount":    1,
        "amountVsAvgRatio": 1.5,
    }}}


class ScoreResponseBody(BaseModel):
    score:        float
    decision:     str
    reasons:      List[str]
    latencyMs:    float
    ruleScore:    float       # rule-only score, for observability
    mlScore:      float       # ML-only score, for observability
    service:      str = "risk-engine"
    scorerVersion: str = "3.0.0"


# ── Scoring endpoint ──────────────────────────────────────────────────────────
@app.post("/score", response_model=ScoreResponseBody, status_code=200)
async def score_transfer(body: ScoreRequestBody, request: Request):
    """
    Two-layer fraud score for a transfer request.

    Returns combined score 0.00–1.00.
      decision = APPROVE  when combined score ≤ 0.85
      decision = REJECT   when combined score  > 0.85

    Also returns ruleScore and mlScore separately for observability/debugging.
    """
    correlation_id = request.headers.get("X-Correlation-ID", "unknown")

    # Extract W3C traceparent from incoming request
    carrier = dict(request.headers)
    ctx     = propagator.extract(carrier=carrier, getter=DefaultGetter())

    start = time.perf_counter()

    with tracer.start_as_current_span("risk.score", context=ctx) as span:
        span.set_attribute("accountId",        body.accountId)
        span.set_attribute("amount",           body.amount)
        span.set_attribute("currency",         body.currency)
        span.set_attribute("correlationId",    correlation_id)
        span.set_attribute("accountAgeHours",  body.accountAgeHours  or -1)
        span.set_attribute("recentTxCount",    body.recentTxCount    or -1)
        span.set_attribute("amountVsAvgRatio", body.amountVsAvgRatio or -1)

        # ── Layer 1: rule-based score ──────────────────────────────────────
        rule_req = ScoreRequest(
            accountId=body.accountId,
            customerId=body.customerId,
            amount=body.amount,
            currency=body.currency,
            transactionType=body.transactionType,
            targetAccountId=body.targetAccountId,
            accountAgeHours=body.accountAgeHours,
            recentTxCount=body.recentTxCount,
            amountVsAvgRatio=body.amountVsAvgRatio,
        )
        rule_result = score(rule_req)

        # ── Layer 2: Isolation Forest anomaly score ────────────────────────
        import datetime
        current_hour = datetime.datetime.now().hour

        ml_req = MLScoreRequest(
            amount=body.amount,
            hour_of_day=current_hour,
            account_age_hours=body.accountAgeHours,
            recent_tx_count=body.recentTxCount,
            amount_vs_avg_ratio=body.amountVsAvgRatio,
            is_new_recipient=False,   # future: pass from caller
        )
        ml_result = score_ml(ml_req)

        # ── Combine scores ─────────────────────────────────────────────────
        # weighted ML score so a borderline anomaly alone can't reject
        ml_score_weighted = ml_result.score * ML_WEIGHT
        combined_score    = max(rule_result.score, ml_score_weighted)
        combined_score    = round(min(combined_score, 1.0), 4)

        # Merge reasons — rule reasons first, ML anomaly reasons appended
        # De-duplicate while preserving order
        seen: set = set()
        all_reasons: List[str] = []
        for r in rule_result.reasons + ml_result.reasons:
            if r not in seen:
                seen.add(r)
                all_reasons.append(r)

        # Final decision based on combined score
        REJECT_THRESHOLD = 0.85
        if rule_result.decision == "REJECT" or combined_score > REJECT_THRESHOLD:
            decision = "REJECT"
        else:
            decision = "APPROVE"

        latency_ms = round((time.perf_counter() - start) * 1000, 2)

        span.set_attribute("risk.rule_score",     rule_result.score)
        span.set_attribute("risk.ml_score",       ml_result.score)
        span.set_attribute("risk.combined_score", combined_score)
        span.set_attribute("risk.decision",       decision)

        log.info(
            "rule_score=%.4f ml_score=%.4f ml_weighted=%.4f combined=%.4f "
            "decision=%s amount=%.2f accountId=%d reasons=%s latencyMs=%.2f corrId=%s",
            rule_result.score, ml_result.score, ml_score_weighted, combined_score,
            decision, body.amount, body.accountId,
            all_reasons, latency_ms, correlation_id,
        )

        return ScoreResponseBody(
            score=combined_score,
            decision=decision,
            reasons=all_reasons,
            latencyMs=latency_ms,
            ruleScore=round(rule_result.score, 4),
            mlScore=round(ml_result.score, 4),
        )


# ── Health / root ─────────────────────────────────────────────────────────────
@app.get("/health")
async def health():
    """Docker health check + actuator-compatible response."""
    return {
        "status":  "UP",
        "service": "risk-engine",
        "version": "3.0.0",
        "scorers": ["rule_based", "isolation_forest"],
    }


@app.get("/")
async def root():
    return {
        "service": "risk-engine",
        "version": "3.0.0",
        "docs":    "/docs",
        "scorers": ["rule_based", "isolation_forest"],
    }
