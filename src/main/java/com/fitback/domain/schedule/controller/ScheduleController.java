package com.fitback.domain.schedule.controller;

import com.fitback.domain.schedule.dto.request.ScheduleCreateRequest;
import com.fitback.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.fitback.domain.schedule.dto.response.ScheduleDetailResponse;
import com.fitback.domain.schedule.dto.response.ScheduleListResponse;
import com.fitback.domain.schedule.dto.response.ScheduleResponse;
import com.fitback.domain.schedule.service.ScheduleService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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

    @PostMapping
    @Operation(summary = "스케줄러 일정 등록", description = "사용자가 직접 입력한 상담/방문/기타 일정을 등록합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "등록 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 요청값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "사용자 없음")
    })
    public ResponseEntity<ApiResponse<ScheduleResponse>> createSchedule(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody ScheduleCreateRequest request
    ) {
        ScheduleResponse response = scheduleService.createSchedule(storeId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/{scheduleId}")
    @Operation(summary = "스케줄러 일정 수정", description = "사용자가 직접 등록한 일정을 수정합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "수정 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 요청값"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일정 없음")
    })
    public ResponseEntity<ApiResponse<ScheduleResponse>> updateSchedule(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "수정할 일정 ID")
            @PathVariable UUID scheduleId,
            @Valid @RequestBody ScheduleUpdateRequest request
    ) {
        ScheduleResponse response = scheduleService.updateSchedule(storeId, scheduleId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @DeleteMapping("/{scheduleId}")
    @Operation(summary = "스케줄러 일정 삭제", description = "사용자가 직접 등록한 일정을 삭제합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "삭제 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "일정 없음")
    })
    public ResponseEntity<Void> deleteSchedule(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "삭제할 일정 ID")
            @PathVariable UUID scheduleId
    ) {
        scheduleService.deleteSchedule(storeId, scheduleId);
        return ResponseEntity.noContent().build();
    }
}
