import pytest
from scorer import ScoreRequest, score

def test_score_normal_transaction():
    req = ScoreRequest(
        accountId=1,
        customerId=1,
        amount=500.0,
        currency="PHP",
        transactionType="TRANSFER",
        targetAccountId=2,
        accountAgeHours=100.0,
        recentTxCount=1
    )
    result = score(req)
    assert result.decision == "APPROVE"
    assert result.score < 0.85

def test_score_self_transfer_rejected():
    req = ScoreRequest(
        accountId=1,
        customerId=1,
        amount=500.0,
        currency="PHP",
        transactionType="TRANSFER",
        targetAccountId=1,
        accountAgeHours=100.0,
        recentTxCount=1
    )
    result = score(req)
    assert result.decision == "REJECT"
    assert result.score >= 0.85
    assert "self_transfer" in result.reasons

def test_score_high_amount():
    req = ScoreRequest(
        accountId=1,
        customerId=1,
        amount=150000.0,
        currency="PHP",
        transactionType="TRANSFER",
        targetAccountId=2,
        accountAgeHours=100.0,
        recentTxCount=1
    )
    result = score(req)
    assert "amount_above_100k" in result.reasons
    assert result.score >= 0.50

def test_score_non_php_currency():
    req = ScoreRequest(
        accountId=1,
        customerId=1,
        amount=500.0,
        currency="USD",
        transactionType="TRANSFER",
        targetAccountId=2
    )
    result = score(req)
    assert "non_php_currency" in result.reasons
