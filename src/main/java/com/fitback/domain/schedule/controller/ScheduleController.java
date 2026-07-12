package com.fitback.domain.schedule.controller;

import com.fitback.domain.schedule.dto.response.ScheduleDetailResponse;
import com.fitback.domain.schedule.dto.response.ScheduleListResponse;
import com.fitback.domain.schedule.service.ScheduleService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/schedules")
@RequiredArgsConstructor
@Tag(name = "Schedule", description = "스케줄러 일정 API")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @GetMapping
    @Operation(summary = "스케줄러 일정 목록 조회", description = "선택 기간과 겹치는 매장 일정을 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 조회 기간"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<ScheduleListResponse>> getSchedules(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회 시작일(YYYY-MM-DD)", example = "2026-10-12")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "조회 종료일(YYYY-MM-DD)", example = "2026-10-18")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        ScheduleListResponse response = scheduleService.getSchedules(storeId, startDate, endDate);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/{scheduleId}")
    @Operation(summary = "스케줄러 일정 상세 조회", description = "일정 카드 클릭 시 상세 팝업에 필요한 데이터를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일정 없음")
    })
    public ResponseEntity<ApiResponse<ScheduleDetailResponse>> getScheduleDetail(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회할 일정 ID")
            @PathVariable UUID scheduleId
    ) {
        ScheduleDetailResponse response = scheduleService.getScheduleDetail(storeId, scheduleId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
