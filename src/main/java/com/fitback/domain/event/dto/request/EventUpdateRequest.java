package com.fitback.domain.event.dto.request;

import com.fitback.domain.event.enums.EventStatus;
import com.fitback.domain.event.enums.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class EventUpdateRequest {

    @NotBlank(message = "이벤트명을 입력하세요.")
    @Size(max = 200, message = "이벤트명은 200자 이하여야 합니다.")
    private String title;

    @NotNull(message = "이벤트 유형을 선택하세요.")
    private EventType eventType;

    private String description;

    private BigDecimal discountRate;

    private UUID serviceId;

    @NotNull(message = "시작일을 입력하세요.")
    private LocalDate startDate;

    @NotNull(message = "종료일을 입력하세요.")
    private LocalDate endDate;

    // ACTIVE 또는 INACTIVE만 허용 (ENDED는 endDate 기준 자동 계산)
    @NotNull(message = "상태를 선택하세요.")
    private EventStatus status;
}