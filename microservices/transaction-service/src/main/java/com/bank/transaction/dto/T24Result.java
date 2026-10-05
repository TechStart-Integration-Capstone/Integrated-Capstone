package com.bank.transaction.dto;

public record T24Result(String status, String ftReference, String ofsResponse, String reason, boolean cachedResponse) {}
