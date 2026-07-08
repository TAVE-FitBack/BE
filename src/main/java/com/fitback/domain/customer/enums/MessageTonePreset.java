package com.fitback.domain.customer.enums;

public enum MessageTonePreset {
    FRIENDLY("친근한 말투"),
    PROFESSIONAL("전문적인 말투"),
    SOFT("부드러운 말투");

    private final String label;

    MessageTonePreset(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
