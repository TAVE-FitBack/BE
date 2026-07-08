package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.CustomerAiAnalysisUpdateRequest;
import com.fitback.domain.customer.dto.request.NextActionRegenerateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.response.CustomerAiAnalysisUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.NextActionRegenerateResponse;
import com.fitback.domain.customer.dto.response.ReconsultationCreateResponse;
import com.fitback.domain.customer.service.CustomerService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "고객 API")
public class CustomerController {

    private final CustomerService customerService;

    @GetMapping("/{customerId}/detail")
    @Operation(summary = "상담내용 상세 조회", description = "고객 단위 상담내용 상세 화면에 필요한 데이터를 조회합니다.")
    public ResponseEntity<ApiResponse<CustomerDetailResponse>> getCustomerDetail(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @PathVariable UUID customerId
    ) {
        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/consultations/check-preview")
    @Operation(summary = "재상담 내용 AI 중간 점검", description = "기존 고객의 재상담 원문을 AI 서버에 전달해 작성 가이드라인 충족 여부를 점검합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkReconsultationPreview(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @PathVariable UUID customerId,
            @Valid @RequestBody ReconsultationCheckPreviewRequest request
    ) {
        Map<String, Object> response = customerService.checkReconsultationPreview(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/consultations")
    @Operation(summary = "재상담 등록", description = "기존 고객의 새 상담 회차를 저장하고 AI 분석을 비동기로 시작합니다.")
    public ResponseEntity<ApiResponse<ReconsultationCreateResponse>> createReconsultation(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @PathVariable UUID customerId,
            @Valid @RequestBody ReconsultationCreateRequest request
    ) {
        ReconsultationCreateResponse response = customerService.createReconsultation(storeId, customerId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/{customerId}/ai-analysis")
    @Operation(summary = "AI 분석값 수동 수정", description = "AI 상담요약, 고객온도, 주요 이탈요인을 수동 수정합니다. 다음 최적 액션은 자동 변경하지 않습니다.")
    public ResponseEntity<ApiResponse<CustomerAiAnalysisUpdateResponse>> updateAiAnalysis(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @PathVariable UUID customerId,
            @Valid @RequestBody CustomerAiAnalysisUpdateRequest request
    ) {
        CustomerAiAnalysisUpdateResponse response = customerService.updateAiAnalysis(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/next-action/regenerate")
    @Operation(summary = "다음 최적 액션 재생성", description = "최신 고객/상담/AI 분석값을 기준으로 다음 최적 액션을 수동 재생성합니다.")
    public ResponseEntity<ApiResponse<NextActionRegenerateResponse>> regenerateNextAction(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @PathVariable UUID customerId,
            @RequestBody NextActionRegenerateRequest request
    ) {
        NextActionRegenerateResponse response = customerService.regenerateNextAction(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
