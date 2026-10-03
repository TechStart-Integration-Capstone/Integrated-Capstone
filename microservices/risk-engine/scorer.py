"""
PayPink 2.0 — Risk Scoring Engine
Rule-based fraud scorer. Returns a float 0.00–1.00.
Score > 0.85 → REJECT. No ML — pure rules for capstone.

AGENTS.md rule: No risk score → reject (503). Money never moves without a score.
"""

from __future__ import annotations
from dataclasses import dataclass, field
from typing import List


@dataclass
class ScoreRequest:
    accountId: int
    customerId: int
    amount: float
    currency: str
    transactionType: str
    targetAccountId: int | None = None
    accountAgeHours: float | None = None   # populated by caller from account-service
    recentTxCount: int | None = None       # number of transactions in last hour


@dataclass
class ScoreResult:
    score: float
    decision: str          # "APPROVE" or "REJECT"
    reasons: List[str] = field(default_factory=list)

    def to_dict(self) -> dict:
        return {
            "score": round(self.score, 4),
            "decision": self.decision,
            "reasons": self.reasons,
        }


REJECT_THRESHOLD = 0.85


def score(req: ScoreRequest) -> ScoreResult:
    """
    Evaluate a transfer request and return a risk score.

    Rules (additive, capped at 1.00):
      - Self-transfer (same account)          → 0.90  (near-certain reject)
      - Amount > PHP 100,000                  → 0.50
      - Amount > PHP 50,000                   → 0.30
      - Amount > PHP 20,000                   → 0.15
      - New account (< 24 h old)              → 0.25
      - High velocity (> 5 txns last hour)    → 0.30
      - Medium velocity (> 2 txns last hour)  → 0.15
      - Non-PHP currency                      → 0.20
    """
    raw = 0.0
    reasons: List[str] = []

    # Self-transfer guard
    if req.targetAccountId is not None and req.targetAccountId == req.accountId:
        raw += 0.90
        reasons.append("self_transfer")

    # Amount thresholds (PHP)
    if req.amount > 100_000:
        raw += 0.50
        reasons.append("amount_above_100k")
    elif req.amount > 50_000:
        raw += 0.30
        reasons.append("amount_above_50k")
    elif req.amount > 20_000:
        raw += 0.15
        reasons.append("amount_above_20k")

    # New account risk
    if req.accountAgeHours is not None and req.accountAgeHours < 24:
        raw += 0.25
        reasons.append("new_account_under_24h")

    # Transaction velocity
    if req.recentTxCount is not None:
        if req.recentTxCount > 5:
            raw += 0.30
            reasons.append("high_velocity_over_5")
        elif req.recentTxCount > 2:
            raw += 0.15
            reasons.append("medium_velocity_over_2")

    # Currency check
    if req.currency.upper() != "PHP":
        raw += 0.20
        reasons.append("non_php_currency")

    final_score = min(raw, 1.0)
    decision = "REJECT" if final_score > REJECT_THRESHOLD else "APPROVE"

    if not reasons:
        reasons.append("no_risk_signals")

    return ScoreResult(score=final_score, decision=decision, reasons=reasons)
