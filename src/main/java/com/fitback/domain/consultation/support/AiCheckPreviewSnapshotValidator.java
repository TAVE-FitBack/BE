package com.fitback.domain.consultation.support;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewItemRequest;
import com.fitback.domain.consultation.dto.request.AiCheckPreviewSnapshotRequest;
import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import com.fitback.global.exception.BusinessException;
import com.fitback.global.exception.ErrorCode;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public final class AiCheckPreviewSnapshotValidator {

    private static final int MAX_SIGNAL_COUNT = AiCheckSignalKey.values().length;

    private AiCheckPreviewSnapshotValidator() {
    }

    public static void validate(AiCheckPreviewSnapshotRequest snapshot) {
        if (snapshot == null) {
            return;
        }

        List<AiCheckPreviewItemRequest> items = snapshot.getItems();
        if (items == null || items.isEmpty() || items.size() > MAX_SIGNAL_COUNT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        validateItems(items);
        validateCounts(snapshot, items);
    }

    private static void validateItems(List<AiCheckPreviewItemRequest> items) {
        Set<AiCheckSignalKey> keys = EnumSet.noneOf(AiCheckSignalKey.class);
        for (AiCheckPreviewItemRequest item : items) {
            if (item == null
                    || item.getKey() == null
                    || item.getLabel() == null
                    || item.getLabel().isBlank()
                    || item.getConfirmed() == null) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }

            if (!keys.add(item.getKey())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }
    }

    private static void validateCounts(AiCheckPreviewSnapshotRequest snapshot, List<AiCheckPreviewItemRequest> items) {
        Integer confirmedCount = snapshot.getConfirmedCount();
        if (confirmedCount != null) {
            long actualConfirmedCount = items.stream()
                    .filter(item -> Boolean.TRUE.equals(item.getConfirmed()))
                    .count();
            if (confirmedCount != actualConfirmedCount) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
        }

        Integer totalCount = snapshot.getTotalCount();
        if (totalCount != null && totalCount != items.size()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
