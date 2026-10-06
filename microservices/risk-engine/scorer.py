"""
PayPink 2.0 — Rule-Based Risk Scoring Engine
=============================================
Pure rule scorer. Returns a float 0.00–1.00.
Score > 0.85 → REJECT. No I/O — all inputs come from the caller.

AGENTS.md rule: No risk score → reject (503). Money never moves without a score.

Rules (additive, capped at 1.00)
---------------------------------
  self_transfer                 → 0.90  (near-certain reject; also caught upstream)
  amount > PHP 100,000          → 0.50
  amount > PHP 50,000           → 0.30
  amount > PHP 20,000           → 0.15
  new account < 24 h            → 0.25  (requires accountAgeHours to be sent)
  high velocity > 5 txns/hr     → 0.30  (requires recentTxCount to be sent)
  medium velocity > 2 txns/hr   → 0.15  (requires recentTxCount to be sent)
  amount ≥ 10× customer avg     → 0.40  (requires amountVsAvgRatio to be sent)
  amount ≥ 5× customer avg      → 0.25  (requires amountVsAvgRatio to be sent)
  amount ≥ 3× customer avg      → 0.10  (requires amountVsAvgRatio to be sent)
  non-PHP currency              → 0.20

The three enrichment fields (accountAgeHours, recentTxCount, amountVsAvgRatio)
are populated by RiskEngineClient.java via DB queries before calling this service.
When they are None the corresponding rules are simply skipped — no error.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional


@dataclass
class ScoreRequest:
    accountId:          int
    customerId:         int
    amount:             float
    currency:           str
    transactionType:    str
    targetAccountId:    Optional[int]   = None
    # ── Enrichment fields (wired in Layer 1) ──────────────────────────────
    accountAgeHours:    Optional[float] = None  # hours since account was created
    recentTxCount:      Optional[int]   = None  # transfers sent in the last hour
    amountVsAvgRatio:   Optional[float] = None  # this amount / 30-day average amount


@dataclass
class ScoreResult:
    score:    float
    decision: str           # "APPROVE" or "REJECT"
    reasons:  List[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "score":    round(self.score, 4),
            "decision": self.decision,
            "reasons":  self.reasons,
        }


REJECT_THRESHOLD = 0.85


def score(req: ScoreRequest) -> ScoreResult:
    """
    Evaluate a transfer request and return a deterministic risk score.
    Called by main.py which then combines this with the ML scorer output.
    """
    raw     = 0.0
    reasons: List[str] = []

    # ── Self-transfer guard ────────────────────────────────────────────────
    # Note: the orchestrator also rejects same-account transfers before ever
    # calling the risk engine, so this rule is a defence-in-depth backstop.
    if req.targetAccountId is not None and req.targetAccountId == req.accountId:
        raw += 0.90
        reasons.append("self_transfer")

    # ── Amount thresholds (PHP) ────────────────────────────────────────────
    if req.amount > 100_000:
        raw += 0.50
        reasons.append("amount_above_100k")
    elif req.amount > 50_000:
        raw += 0.30
        reasons.append("amount_above_50k")
    elif req.amount > 20_000:
        raw += 0.15
        reasons.append("amount_above_20k")

    # ── New account risk ───────────────────────────────────────────────────
    if req.accountAgeHours is not None and req.accountAgeHours < 24:
        raw += 0.25
        reasons.append("new_account_under_24h")

    # ── Transaction velocity ───────────────────────────────────────────────
    if req.recentTxCount is not None:
        if req.recentTxCount > 5:
            raw += 0.30
            reasons.append("high_velocity_over_5")
        elif req.recentTxCount > 2:
            raw += 0.15
            reasons.append("medium_velocity_over_2")

    # ── Amount vs customer historical average ──────────────────────────────
    # amountVsAvgRatio = this transfer's amount / 30-day average transfer amount.
    # A ratio of 10.0 means this transfer is 10× the customer's usual amount.
    if req.amountVsAvgRatio is not None:
        if req.amountVsAvgRatio >= 10.0:
            raw += 0.40
            reasons.append(f"amount_{req.amountVsAvgRatio:.1f}x_above_customer_avg")
        elif req.amountVsAvgRatio >= 5.0:
            raw += 0.25
            reasons.append(f"amount_{req.amountVsAvgRatio:.1f}x_above_customer_avg")
        elif req.amountVsAvgRatio >= 3.0:
            raw += 0.10
            reasons.append(f"amount_{req.amountVsAvgRatio:.1f}x_above_customer_avg")

    # ── Currency check ─────────────────────────────────────────────────────
    if req.currency.upper() != "PHP":
        raw += 0.20
        reasons.append("non_php_currency")

    final_score = min(raw, 1.0)
    decision    = "REJECT" if final_score > REJECT_THRESHOLD else "APPROVE"

    if not reasons:
        reasons.append("no_risk_signals")

    return ScoreResult(score=final_score, decision=decision, reasons=reasons)
