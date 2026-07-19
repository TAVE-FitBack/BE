package com.fitback.domain.consultation.enums;

import java.util.Arrays;

public enum AiCheckSignalKey {

    INTEREST_SERVICE("관심 상품", 1),
    EXERCISE_GOAL("운동 목적", 2),
    EXERCISE_EXPERIENCE("운동 경험", 3),
    INJURY_HISTORY("부상 경험", 4),
    CUSTOMER_REQUEST("고객 요청", 5),
    COUNSELOR_RESPONSE("나의 응대", 6),
    SPECIAL_NOTE("특이사항", 7);

    private final String label;
    private final int displayOrder;

    AiCheckSignalKey(String label, int displayOrder) {
        this.label = label;
        this.displayOrder = displayOrder;
    }

    public String getLabel() {
        return label;
    }

    public int getDisplayOrder() {
        return displayOrder;
    }

    public static boolean isValid(String key) {
        return Arrays.stream(values())
                .anyMatch(value -> value.name().equals(key));
    }
}
