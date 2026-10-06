package com.bank.notification.service;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Optional;

/**
 * SMS-style customer messages for loan.* events (Phase 6 Loans).
 * Each alert gets its own reference so NOTIFICATION's UNIQUE (reference_no, account_id) holds:
 * the application, loan or repayment reference, plus a suffix for overdue/closed alerts.
 */
public final class LoanNotificationMessages {

    private LoanNotificationMessages() {}

    public record LoanAlert(Long customerId, Long accountId, String referenceNo, String message) {}

    public static boolean isLoanEvent(JsonNode node) {
        return node.hasNonNull("eventType") && node.get("eventType").asText().startsWith("loan.");
    }

    /** Empty for loan.* events that don't notify the customer. */
    public static Optional<LoanAlert> build(JsonNode node) {
        String type = text(node, "eventType");
        Long customerId = node.path("customerId").asLong();
        Long accountId = node.path("accountId").asLong();
        String loanRef = text(node, "loanReferenceNo");

        return Optional.ofNullable(switch (type) {
            case "loan.application.decided" -> {
                String appRef = text(node, "applicationReferenceNo");
                String outcome = switch (text(node, "decision")) {
                    case "APPROVED" -> "was APPROVED";
                    case "COUNTER_OFFER" -> "has a COUNTER OFFER";
                    default -> "was DECLINED";
                };
                yield new LoanAlert(customerId, accountId, appRef,
                        "Your loan application " + appRef + " " + outcome + ".");
            }
            case "loan.disbursed" -> new LoanAlert(customerId, accountId, loanRef,
                    peso(node, "amount") + " has been credited to your account. First payment due "
                            + text(node, "firstDueDate") + ".");
            case "loan.repayment.posted" -> new LoanAlert(customerId, accountId, text(node, "repaymentReferenceNo"),
                    "Payment of " + peso(node, "amount") + " received for loan " + loanRef + ".");
            case "loan.installment.overdue" -> {
                BigDecimal penalty = decimal(node, "penalty");
                String message = "Your installment due " + text(node, "dueDate") + " is overdue."
                        + (penalty.signum() > 0 ? " A penalty of " + peso(penalty) + " was added." : "");
                yield new LoanAlert(customerId, accountId, loanRef + "-I" + node.path("installmentNo").asInt(), message);
            }
            case "loan.closed" -> new LoanAlert(customerId, accountId, loanRef + "-CLOSED",
                    "Loan " + loanRef + " is fully paid. Thank you!");
            default -> null;
        });
    }

    private static String text(JsonNode node, String field) {
        return node.hasNonNull(field) ? node.get(field).asText() : "";
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        return node.hasNonNull(field) ? new BigDecimal(node.get(field).asText()) : BigDecimal.ZERO;
    }

    private static String peso(JsonNode node, String field) {
        return peso(decimal(node, field));
    }

    private static String peso(BigDecimal amount) {
        return "₱" + String.format(Locale.US, "%,.2f", amount);
    }
}
