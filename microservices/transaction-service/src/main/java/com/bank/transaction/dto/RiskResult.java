package com.bank.transaction.dto;

import java.math.BigDecimal;
import java.util.List;

public record RiskResult(BigDecimal score, String decision, List<String> reasons) {}
