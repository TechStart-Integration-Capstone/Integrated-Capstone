package com.bank.ledger.event;

import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;

/**
 * Strict readers for Kafka event JSON.
 * A missing/null field throws IllegalArgumentException instead of silently defaulting
 * (e.g. to account 1 or "TX-REF"). Consumers treat IllegalArgumentException as
 * non-retryable, so the bad event goes straight to the dead-letter topic.
 */
public final class EventFields {

    private EventFields() {}

    public static JsonNode required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            throw new IllegalArgumentException("Event is missing required field '" + field + "'");
        }
        return value;
    }

    public static Long requiredLong(JsonNode node, String field) {
        JsonNode value = required(node, field);
        if (!value.canConvertToLong()) {
            throw new IllegalArgumentException("Event field '" + field + "' is not a valid number: " + value);
        }
        return value.asLong();
    }

    public static String requiredText(JsonNode node, String field) {
        String text = required(node, field).asText();
        if (text.isBlank()) {
            throw new IllegalArgumentException("Event field '" + field + "' is blank");
        }
        return text;
    }

    /** Exact decimal: uses the parsed BigDecimal (requires use-big-decimal-for-floats) or the raw text. */
    public static BigDecimal requiredDecimal(JsonNode node, String field) {
        JsonNode value = required(node, field);
        try {
            return value.isNumber() ? value.decimalValue() : new BigDecimal(value.asText());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Event field '" + field + "' is not a valid decimal: " + value);
        }
    }
}
