package com.fitback.domain.customer.enums;

public enum MessageVersionType {
    SHORT("짧은 버전"),
    STANDARD("표준 버전");

    private final String label;

    MessageVersionType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
