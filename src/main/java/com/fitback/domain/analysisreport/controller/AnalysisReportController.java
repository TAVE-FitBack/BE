package com.fitback.domain.analysisreport.controller;

import com.fitback.domain.analysisreport.dto.response.AnalysisReportFollowUpResponse;
import com.fitback.domain.analysisreport.service.AnalysisReportFollowUpService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/analysis-report")
@RequiredArgsConstructor
@Tag(name = "Analysis Report", description = "분석 리포트 API")
public class AnalysisReportController {

    private final AnalysisReportFollowUpService analysisReportFollowUpService;

    @GetMapping("/follow-up")
    @Operation(summary = "분석 리포트 후속관리 탭 조회", description = "선택 월 기준 후속관리 분석 리포트 통계와 차트 데이터를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 월 형식 오류"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<AnalysisReportFollowUpResponse>> getFollowUpReport(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회 월(YYYY-MM). 미입력 시 현재 월", example = "2026-10")
            @RequestParam(required = false) String month
    ) {
        AnalysisReportFollowUpResponse response = analysisReportFollowUpService.getFollowUpReport(storeId, month);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
