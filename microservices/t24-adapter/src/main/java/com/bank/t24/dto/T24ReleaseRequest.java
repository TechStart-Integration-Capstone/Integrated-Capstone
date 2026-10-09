package com.bank.t24.dto;

import jakarta.validation.constraints.NotBlank;

public class T24ReleaseRequest {

    @NotBlank(message = "Reference number is required")
    private String referenceNo;

    public T24ReleaseRequest() {}

    public T24ReleaseRequest(String referenceNo) {
        this.referenceNo = referenceNo;
    }

    public String getReferenceNo() { return referenceNo; }
    public void setReferenceNo(String referenceNo) { this.referenceNo = referenceNo; }
}
