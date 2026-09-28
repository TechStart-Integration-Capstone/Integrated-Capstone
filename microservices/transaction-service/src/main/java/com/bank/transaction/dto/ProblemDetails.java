package com.bank.transaction.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProblemDetails {

    private String type;
    private String title;
    private int status;
    private String detail;
    private String instance;
    private LocalDateTime timestamp = LocalDateTime.now();
    private List<ValidationError> invalidParams = new ArrayList<>();

    public ProblemDetails() {}

    public ProblemDetails(String type, String title, int status, String detail, String instance) {
        this.type = type;
        this.title = title;
        this.status = status;
        this.detail = detail;
        this.instance = instance;
        this.timestamp = LocalDateTime.now();
    }

    public void addInvalidParam(String name, String reason, Object rejectedValue) {
        this.invalidParams.add(new ValidationError(name, reason, rejectedValue));
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public int getStatus() { return status; }
    public void setStatus(int status) { this.status = status; }

    public String getDetail() { return detail; }
    public void setDetail(String detail) { this.detail = detail; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public LocalDateTime getTimestamp() { return timestamp; }
    public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

    public List<ValidationError> getInvalidParams() { return invalidParams; }
    public void setInvalidParams(List<ValidationError> invalidParams) { this.invalidParams = invalidParams; }

    public static class ValidationError {
        private String name;
        private String reason;
        private Object rejectedValue;

        public ValidationError() {}

        public ValidationError(String name, String reason, Object rejectedValue) {
            this.name = name;
            this.reason = reason;
            this.rejectedValue = rejectedValue;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public String getReason() { return reason; }
        public void setReason(String reason) { this.reason = reason; }

        public Object getRejectedValue() { return rejectedValue; }
        public void setRejectedValue(Object rejectedValue) { this.rejectedValue = rejectedValue; }
    }
}
