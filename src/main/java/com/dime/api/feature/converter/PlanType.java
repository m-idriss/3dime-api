package com.dime.api.feature.converter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanType {
    FREE,
    PRO,
    UNLIMITED;

    @JsonCreator
    public static PlanType fromString(String value) {
        if (value == null) {
            return FREE; // default value
        }
        
        try {
            return PlanType.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle common variations
            return switch (value.toLowerCase()) {
                case "free" -> FREE;
                case "pro" -> PRO;
                case "premium" -> PRO;
                case "unlimited" -> UNLIMITED;
                default -> FREE; // fallback to FREE for unknown values
            };
        }
    }

    @JsonValue
    public String toValue() {
        return this.name();
    }
}
