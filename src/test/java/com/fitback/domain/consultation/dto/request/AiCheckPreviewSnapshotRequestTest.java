package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AiCheckPreviewSnapshotRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validatesUniqueSignalKeys() {
        AiCheckPreviewSnapshotRequest request = snapshot(
                item(AiCheckSignalKey.EXERCISE_GOAL, true),
                item(AiCheckSignalKey.EXERCISE_GOAL, false)
        );
        ReflectionTestUtils.setField(request, "confirmedCount", 1);
        ReflectionTestUtils.setField(request, "totalCount", 2);

        Set<ConstraintViolation<AiCheckPreviewSnapshotRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation -> "uniqueKey".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void validatesConfirmedCountMatchesItems() {
        AiCheckPreviewSnapshotRequest request = snapshot(
                item(AiCheckSignalKey.EXERCISE_GOAL, true),
                item(AiCheckSignalKey.INJURY_HISTORY, false)
        );
        ReflectionTestUtils.setField(request, "confirmedCount", 2);
        ReflectionTestUtils.setField(request, "totalCount", 2);

        Set<ConstraintViolation<AiCheckPreviewSnapshotRequest>> violations = validator.validate(request);

        assertThat(violations)
                .anyMatch(violation -> "confirmedCountMatched".equals(violation.getPropertyPath().toString()));
    }

    @Test
    void resolvesDisplayOrderFromSignalKey() {
        AiCheckPreviewItemRequest item = item(AiCheckSignalKey.INTEREST_SERVICE, true);

        assertThat(item.getDisplayOrder()).isEqualTo(1);
    }

    private AiCheckPreviewSnapshotRequest snapshot(AiCheckPreviewItemRequest... items) {
        AiCheckPreviewSnapshotRequest request = new AiCheckPreviewSnapshotRequest();
        ReflectionTestUtils.setField(request, "items", List.of(items));
        return request;
    }

    private AiCheckPreviewItemRequest item(AiCheckSignalKey key, boolean confirmed) {
        AiCheckPreviewItemRequest request = new AiCheckPreviewItemRequest();
        ReflectionTestUtils.setField(request, "key", key);
        ReflectionTestUtils.setField(request, "label", key.getLabel());
        ReflectionTestUtils.setField(request, "confirmed", confirmed);
        ReflectionTestUtils.setField(request, "value", confirmed ? "확인됨" : "아직 확인되지 않음");
        return request;
    }
}
