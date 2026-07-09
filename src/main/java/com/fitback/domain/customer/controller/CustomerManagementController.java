package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.ConsultationListQuery;
import com.fitback.domain.customer.dto.request.InquiryListQuery;
import com.fitback.domain.customer.dto.response.CustomerManagementConsultationListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementInquiryListResponse;
import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.service.CustomerManagementService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springdoc.core.annotations.ParameterObject;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/customer-management")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "상담고객관리 API")
public class CustomerManagementController {

    private final CustomerManagementService customerManagementService;

    @GetMapping("/summary")
    @Operation(summary = "상담고객관리 상단 통계 조회", description = "선택한 월의 상담, 등록, 종목 및 방문경로 통계를 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "매장 미할당 또는 잘못된 조회 월"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<CustomerManagementSummaryResponse>> getSummary(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "조회 월(YYYY-MM). 미입력 시 현재 월", example = "2026-10")
            @RequestParam(required = false) String month
    ) {
        CustomerManagementSummaryResponse response = customerManagementService.getSummary(storeId, month);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/consultations")
    @Operation(summary = "상담 고객 목록 조회", description = "선택한 월의 상담 고객을 고객당 최신 상담 1건 기준으로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색·필터·페이지 조건"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<CustomerManagementConsultationListResponse>> getConsultations(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @ParameterObject ConsultationListQuery query
    ) {
        CustomerManagementConsultationListResponse response =
                customerManagementService.getConsultations(storeId, query);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/inquiries")
    @Operation(summary = "문의 목록 조회", description = "선택한 월의 미전환 문의를 문의 기록 단위로 조회합니다.")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "조회 성공"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "잘못된 검색·필터·페이지 조건"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "인증 필요")
    })
    public ResponseEntity<ApiResponse<CustomerManagementInquiryListResponse>> getInquiries(
            @Parameter(hidden = true)
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @ParameterObject InquiryListQuery query
    ) {
        CustomerManagementInquiryListResponse response =
                customerManagementService.getInquiries(storeId, query);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
