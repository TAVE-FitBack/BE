package com.fitback.domain.consultation.dto.request;

import com.fitback.domain.consultation.enums.AiCheckSignalKey;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

@Getter
@NoArgsConstructor
public class AiCheckPreviewSnapshotRequest {

    @Min(value = 0, message = "확인된 AI 중간분석 항목 수는 0 이상이어야 합니다.")
    @Max(value = 7, message = "확인된 AI 중간분석 항목 수는 7 이하여야 합니다.")
    private Integer confirmedCount;

    @Min(value = 1, message = "전체 AI 중간분석 항목 수는 1 이상이어야 합니다.")
    @Max(value = 7, message = "전체 AI 중간분석 항목 수는 7 이하여야 합니다.")
    private Integer totalCount;

    @Valid
    @NotEmpty(message = "AI 중간분석 항목은 1개 이상이어야 합니다.")
    @Size(max = 7, message = "AI 중간분석 항목은 최대 7개까지 허용됩니다.")
    private List<@Valid @NotNull AiCheckPreviewItemRequest> items;

    @AssertTrue(message = "AI 중간분석 key는 중복될 수 없습니다.")
    public boolean isUniqueKey() {
        if (items == null || items.isEmpty()) {
            return true;
        }

        Set<AiCheckSignalKey> keys = EnumSet.noneOf(AiCheckSignalKey.class);
        for (AiCheckPreviewItemRequest item : items) {
            if (item == null || item.getKey() == null) {
                continue;
            }
            if (!keys.add(item.getKey())) {
                return false;
            }
        }
        return true;
    }

    @AssertTrue(message = "확인된 AI 중간분석 항목 수가 items와 일치하지 않습니다.")
    public boolean isConfirmedCountMatched() {
        if (confirmedCount == null || items == null) {
            return true;
        }

        long actualConfirmedCount = items.stream()
                .filter(item -> item != null && Boolean.TRUE.equals(item.getConfirmed()))
                .count();
        return confirmedCount == actualConfirmedCount;
    }

    @AssertTrue(message = "전체 AI 중간분석 항목 수가 items와 일치하지 않습니다.")
    public boolean isTotalCountMatched() {
        if (totalCount == null || items == null) {
            return true;
        }

        return totalCount == items.size();
    }
}
