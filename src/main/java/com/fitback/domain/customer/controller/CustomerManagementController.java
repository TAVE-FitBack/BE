package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.response.CustomerManagementSummaryResponse;
import com.fitback.domain.customer.service.CustomerManagementService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/customer-management")
@RequiredArgsConstructor
@Tag(name = "Customer Management", description = "상담고객관리 API")
public class CustomerManagementController {

    private final CustomerManagementService customerManagementService;

    @GetMapping("/summary")
    @Operation(summary = "상담고객관리 상단 통계 조회", description = "선택한 월의 상담, 등록, 종목 및 방문경로 통계를 조회합니다.")
    public ResponseEntity<ApiResponse<CustomerManagementSummaryResponse>> getSummary(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @RequestParam(required = false) String month
    ) {
        CustomerManagementSummaryResponse response = customerManagementService.getSummary(storeId, month);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
