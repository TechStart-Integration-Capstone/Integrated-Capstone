"""
PayPink 2.0 — ML Risk Scorer (Isolation Forest)
================================================
Parallel anomaly-detection layer that runs alongside the rule-based scorer.py.

Design
------
* Unsupervised Isolation Forest — no labeled fraud data required.
* Trained on synthetic transaction data that mirrors realistic PayPink
  customer behaviour, with injected anomaly patterns.
* Model is trained once at import time and cached in memory.
  On first run the fitted model is serialized to MODEL_PATH so subsequent
  container starts skip retraining (warm start).
* Output is a normalized anomaly score 0.0–1.0 (higher = more anomalous)
  plus a list of human-readable reason strings consumed by main.py.

Feature vector (6 dimensions)
------------------------------
  0  amount_php          — raw transfer amount in PHP
  1  hour_of_day         — 0-23, local time of the request
  2  account_age_hours   — hours since account was created (0 if unknown)
  3  recent_tx_count     — transfers in the last hour (0 if unknown)
  4  amount_vs_avg_ratio — amount / 30-day average (1.0 if no history)
  5  is_new_recipient    — placeholder, always 0.0 for now (future use)

Combination with rule score
----------------------------
  combined = max(rule_score, ml_score * ML_WEIGHT)
  decision = REJECT if combined > 0.85 else APPROVE

The ML score is down-weighted slightly (ML_WEIGHT = 0.90) so that a borderline
anomaly alone cannot reject a transfer — it must combine with at least one rule
signal to cross the threshold.  This reduces false positives from the
unsupervised model while still catching patterns the rules miss entirely.
"""

from __future__ import annotations

import logging
import math
import os
import pathlib
import time
from dataclasses import dataclass, field
from typing import List, Optional, Tuple

import joblib
import numpy as np
from sklearn.ensemble import IsolationForest

log = logging.getLogger("risk-engine.ml")

# ── Configuration ─────────────────────────────────────────────────────────────

MODEL_PATH   = pathlib.Path(os.getenv("ML_MODEL_PATH", "/tmp/paypink_if_model.joblib"))
ML_WEIGHT    = float(os.getenv("ML_SCORE_WEIGHT", "0.90"))   # scale IF score before combining
N_ESTIMATORS = int(os.getenv("IF_N_ESTIMATORS", "200"))      # more trees = more stable scores
CONTAMINATION = float(os.getenv("IF_CONTAMINATION", "0.05")) # assume ~5% anomalous transactions
RANDOM_SEED  = 42

# ── Feature definition ────────────────────────────────────────────────────────

FEATURE_NAMES = [
    "amount_php",
    "hour_of_day",
    "account_age_hours",
    "recent_tx_count",
    "amount_vs_avg_ratio",
    "is_new_recipient",
]

N_FEATURES = len(FEATURE_NAMES)


# ── Public interface ──────────────────────────────────────────────────────────

@dataclass
class MLScoreRequest:
    amount: float
    hour_of_day: int
    account_age_hours: Optional[float] = None   # None → imputed as 8760 (1 year old account)
    recent_tx_count: Optional[int]     = None   # None → imputed as 0
    amount_vs_avg_ratio: Optional[float] = None # None → imputed as 1.0 (no history)
    is_new_recipient: bool             = False


@dataclass
class MLScoreResult:
    score: float                         # 0.0–1.0, higher = more anomalous
    reasons: List[str] = field(default_factory=list)
    model_version: str = "isolation_forest_v1"


def score_ml(req: MLScoreRequest) -> MLScoreResult:
    """
    Score a single transaction using the Isolation Forest model.

    Returns MLScoreResult with a normalized anomaly score and
    human-readable reason strings for any triggered signals.
    """
    model = _get_model()
    features = _build_feature_vector(req)
    reasons  = _explain_features(req, features)

    # decision_function returns negative = anomalous, positive = normal
    raw_score = model.decision_function([features])[0]

    # Normalize to 0.0–1.0: decision_function range is roughly -0.5 to +0.5
    # We map it so that -0.5 → 1.0 (very anomalous) and +0.5 → 0.0 (very normal)
    normalized = float(np.clip(0.5 - raw_score, 0.0, 1.0))

    log.debug("IF raw_score=%.4f normalized=%.4f features=%s", raw_score, normalized, features)
    return MLScoreResult(score=normalized, reasons=reasons)


# ── Feature engineering ───────────────────────────────────────────────────────

def _build_feature_vector(req: MLScoreRequest) -> List[float]:
    """Convert an MLScoreRequest into the 6-dimensional feature vector."""
    # Impute missing values with safe defaults
    age_hours   = req.account_age_hours  if req.account_age_hours  is not None else 8760.0
    tx_count    = req.recent_tx_count    if req.recent_tx_count    is not None else 0
    avg_ratio   = req.amount_vs_avg_ratio if req.amount_vs_avg_ratio is not None else 1.0
    new_recip   = 1.0 if req.is_new_recipient else 0.0

    return [
        float(req.amount),
        float(req.hour_of_day),
        float(age_hours),
        float(tx_count),
        float(avg_ratio),
        float(new_recip),
    ]


def _explain_features(req: MLScoreRequest, features: List[float]) -> List[str]:
    """
    Build reason strings from feature values.
    These supplement (not replace) the rule-based reasons from scorer.py.
    Only reasons that are meaningfully above normal are included.
    """
    reasons: List[str] = []

    # Off-hours signal: 0–5am local time
    hour = req.hour_of_day
    if 0 <= hour <= 5:
        reasons.append(f"anomaly_off_hours_{hour:02d}h")

    # Account age
    age = req.account_age_hours if req.account_age_hours is not None else 8760.0
    if age < 1:
        reasons.append("anomaly_account_under_1h")
    elif age < 24:
        reasons.append("anomaly_account_under_24h")

    # Velocity
    count = req.recent_tx_count if req.recent_tx_count is not None else 0
    if count >= 8:
        reasons.append(f"anomaly_very_high_velocity_{count}_last_1h")
    elif count >= 4:
        reasons.append(f"anomaly_high_velocity_{count}_last_1h")

    # Amount vs historical average
    ratio = req.amount_vs_avg_ratio if req.amount_vs_avg_ratio is not None else 1.0
    if ratio >= 10.0:
        reasons.append(f"anomaly_amount_{ratio:.1f}x_above_avg")
    elif ratio >= 5.0:
        reasons.append(f"anomaly_amount_{ratio:.1f}x_above_avg")
    elif ratio >= 3.0:
        reasons.append(f"anomaly_amount_{ratio:.1f}x_above_avg")

    # New recipient
    if req.is_new_recipient:
        reasons.append("anomaly_first_transfer_to_recipient")

    return reasons


# ── Model lifecycle ───────────────────────────────────────────────────────────

_cached_model: Optional[IsolationForest] = None


def _get_model() -> IsolationForest:
    """Return the cached model, loading from disk or training if needed."""
    global _cached_model
    if _cached_model is not None:
        return _cached_model

    if MODEL_PATH.exists():
        log.info("Loading Isolation Forest model from %s", MODEL_PATH)
        _cached_model = joblib.load(MODEL_PATH)
        return _cached_model

    log.info("No saved model found — training Isolation Forest on synthetic data…")
    _cached_model = _train_and_save()
    return _cached_model


def _train_and_save() -> IsolationForest:
    """
    Generate synthetic Philippine retail banking transaction data, train
    an Isolation Forest, and persist the model to MODEL_PATH.

    Synthetic data composition
    --------------------------
    90% normal transactions — low-to-medium amounts, business hours,
        established accounts, low velocity, ratio near 1.0

    10% anomalous patterns — four injected fraud archetypes:
        A. Large off-hours transfer from a new account
        B. High-velocity small transfers (rapid succession)
        C. Transfer 10-20× above the customer's normal amount
        D. Combined: new account + large amount + high velocity
    """
    t0 = time.perf_counter()
    rng = np.random.default_rng(RANDOM_SEED)
    N_NORMAL   = 9000
    N_ANOMALY  = 1000
    N_TOTAL    = N_NORMAL + N_ANOMALY

    # ── Normal transactions ──────────────────────────────────────────────────
    normal_amount   = rng.lognormal(mean=9.5,  sigma=1.2,  size=N_NORMAL)   # ~₱7k–60k
    normal_hour     = rng.choice(range(8, 22), size=N_NORMAL)               # 8am–10pm
    normal_age      = rng.uniform(720, 43800,  size=N_NORMAL)               # 1 month–5 years
    normal_velocity = rng.integers(0, 3,        size=N_NORMAL)              # 0–2 per hour
    normal_ratio    = rng.uniform(0.5, 2.0,    size=N_NORMAL)               # near-average
    normal_new_recip = rng.integers(0, 2,      size=N_NORMAL).astype(float) # 50/50

    # ── Anomalous pattern A: large off-hours transfer from new account ───────
    n_a = N_ANOMALY // 4
    anom_a_amount   = rng.uniform(120_000, 500_000, size=n_a)
    anom_a_hour     = rng.choice([0, 1, 2, 3, 4, 5], size=n_a)
    anom_a_age      = rng.uniform(0, 48,  size=n_a)                        # < 2 days old
    anom_a_velocity = rng.integers(0, 2, size=n_a)
    anom_a_ratio    = rng.uniform(8, 20,  size=n_a)
    anom_a_new      = np.ones(n_a)

    # ── Anomalous pattern B: high-velocity small transfers ───────────────────
    n_b = N_ANOMALY // 4
    anom_b_amount   = rng.uniform(50, 500,    size=n_b)
    anom_b_hour     = rng.choice(range(0, 24), size=n_b)
    anom_b_age      = rng.uniform(24, 720,    size=n_b)
    anom_b_velocity = rng.integers(8, 20,     size=n_b)
    anom_b_ratio    = rng.uniform(0.1, 0.5,   size=n_b)
    anom_b_new      = np.zeros(n_b)

    # ── Anomalous pattern C: single transfer far above customer average ──────
    n_c = N_ANOMALY // 4
    anom_c_amount   = rng.uniform(80_000, 300_000, size=n_c)
    anom_c_hour     = rng.choice(range(8, 22),     size=n_c)
    anom_c_age      = rng.uniform(720, 8760,        size=n_c)
    anom_c_velocity = rng.integers(0, 3,            size=n_c)
    anom_c_ratio    = rng.uniform(10, 25,           size=n_c)
    anom_c_new      = rng.integers(0, 2, size=n_c).astype(float)

    # ── Anomalous pattern D: combined new-account + large + high-velocity ────
    n_d = N_ANOMALY - n_a - n_b - n_c
    anom_d_amount   = rng.uniform(50_000, 200_000, size=n_d)
    anom_d_hour     = rng.choice([0, 1, 2, 3, 22, 23], size=n_d)
    anom_d_age      = rng.uniform(0, 12,  size=n_d)
    anom_d_velocity = rng.integers(5, 15, size=n_d)
    anom_d_ratio    = rng.uniform(5, 15,  size=n_d)
    anom_d_new      = np.ones(n_d)

    # ── Assemble the full training matrix ────────────────────────────────────
    X = np.column_stack([
        np.concatenate([normal_amount,   anom_a_amount,   anom_b_amount,   anom_c_amount,   anom_d_amount]),
        np.concatenate([normal_hour,     anom_a_hour,     anom_b_hour,     anom_c_hour,     anom_d_hour]),
        np.concatenate([normal_age,      anom_a_age,      anom_b_age,      anom_c_age,      anom_d_age]),
        np.concatenate([normal_velocity, anom_a_velocity, anom_b_velocity, anom_c_velocity, anom_d_velocity]),
        np.concatenate([normal_ratio,    anom_a_ratio,    anom_b_ratio,    anom_c_ratio,    anom_d_ratio]),
        np.concatenate([normal_new_recip, anom_a_new,     anom_b_new,      anom_c_new,      anom_d_new]),
    ])

    assert X.shape == (N_TOTAL, N_FEATURES), f"Unexpected shape {X.shape}"

    model = IsolationForest(
        n_estimators=N_ESTIMATORS,
        contamination=CONTAMINATION,
        random_state=RANDOM_SEED,
        n_jobs=-1,          # use all CPU cores for training
        max_samples="auto",
    )
    model.fit(X)

    elapsed = round((time.perf_counter() - t0) * 1000, 1)
    log.info(
        "Isolation Forest trained: n_samples=%d n_features=%d "
        "n_estimators=%d contamination=%.2f elapsed_ms=%s",
        N_TOTAL, N_FEATURES, N_ESTIMATORS, CONTAMINATION, elapsed,
    )

    try:
        MODEL_PATH.parent.mkdir(parents=True, exist_ok=True)
        joblib.dump(model, MODEL_PATH)
        log.info("Model saved to %s", MODEL_PATH)
    except Exception as exc:
        log.warning("Could not save model to %s: %s — will retrain on next restart", MODEL_PATH, exc)

    return model


def warm_up() -> None:
    """
    Call this at application startup to ensure the model is trained/loaded
    before the first real request arrives.  Avoids a slow first-request penalty.
    """
    _get_model()
    log.info("ML scorer warm-up complete (model ready)")
