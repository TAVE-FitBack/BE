package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.FollowUpBoardQuery;
import com.fitback.domain.customer.dto.request.FollowUpEndedQuery;
import com.fitback.domain.customer.dto.response.FollowUpBoardResponse;
import com.fitback.domain.customer.dto.response.FollowUpEndedListResponse;
import com.fitback.domain.customer.dto.response.FollowUpSummaryResponse;
import com.fitback.domain.customer.service.FollowUpManagementService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/follow-ups")
@RequiredArgsConstructor
@Tag(name = "FollowUp Management", description = "후속 연락 관리 화면 조회 API")
public class FollowUpManagementController {

    private final FollowUpManagementService followUpManagementService;

    @GetMapping("/summary")
    @Operation(summary = "후속 연락 요약 카드 조회", description = "후속 연락 관리 화면 상단 요약 카드 수치를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 조회 월"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<FollowUpSummaryResponse>> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회 월(YYYY-MM). 미입력 시 현재 월", example = "2026-10")
            @RequestParam(required = false) String month
    ) {
        FollowUpSummaryResponse response = followUpManagementService.getSummary(storeId, month);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/board")
    @Operation(summary = "후속 연락 보드 조회", description = "오늘 연락 또는 연락 예정 대상을 후속 연락 차수별 컬럼으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 검색·필터 조건"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<FollowUpBoardResponse>> getBoard(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @ParameterObject FollowUpBoardQuery query
    ) {
        FollowUpBoardResponse response = followUpManagementService.getBoard(storeId, query);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/ended")
    @Operation(summary = "종료된 후속 연락 목록 조회", description = "완료 또는 종료된 후속 연락 목록을 최근 연락일 기준으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 검색·필터·페이지 조건"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<FollowUpEndedListResponse>> getEndedFollowUps(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @ParameterObject FollowUpEndedQuery query
    ) {
        FollowUpEndedListResponse response = followUpManagementService.getEndedFollowUps(storeId, query);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
