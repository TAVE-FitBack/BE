package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.CustomerAiAnalysisUpdateRequest;
import com.fitback.domain.customer.dto.request.CustomerStatusUpdateRequest;
import com.fitback.domain.customer.dto.request.MessageTemplateCreateRequest;
import com.fitback.domain.customer.dto.request.NextActionRegenerateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCreateRequest;
import com.fitback.domain.customer.dto.request.ReconsultationCheckPreviewRequest;
import com.fitback.domain.customer.dto.response.CustomerAiAnalysisUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerStatusUpdateResponse;
import com.fitback.domain.customer.dto.response.CustomerDetailResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateCreateResponse;
import com.fitback.domain.customer.dto.response.MessageTemplateOptionsResponse;
import com.fitback.domain.customer.dto.response.NextActionRegenerateResponse;
import com.fitback.domain.customer.dto.response.ReconsultationCreateResponse;
import com.fitback.domain.customer.service.CustomerService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId
    ) {
        CustomerDetailResponse response = customerService.getCustomerDetail(storeId, customerId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/{customerId}/message-template/options")
    @Operation(summary = "메시지 생성 옵션 조회", description = "메시지 초안 생성에 사용할 말투, 길이 버전, 활성 이벤트/혜택 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<MessageTemplateOptionsResponse>> getMessageTemplateOptions(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId
    ) {
        MessageTemplateOptionsResponse response = customerService.getMessageTemplateOptions(storeId, customerId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/message-templates")
    @Operation(summary = "메시지 초안 생성", description = "현재 후속 연락과 다음 최적 액션을 기준으로 AI 메시지 초안을 생성합니다.")
    public ResponseEntity<ApiResponse<MessageTemplateCreateResponse>> createMessageTemplate(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @Valid @RequestBody MessageTemplateCreateRequest request
    ) {
        MessageTemplateCreateResponse response = customerService.createMessageTemplate(
                storeId,
                userId,
                customerId,
                request
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/consultations/check-preview")
    @Operation(summary = "재상담 내용 AI 중간 점검", description = "기존 고객의 재상담 원문을 AI 서버에 전달해 작성 가이드라인 충족 여부를 점검합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkReconsultationPreview(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @Valid @RequestBody ReconsultationCheckPreviewRequest request
    ) {
        Map<String, Object> response = customerService.checkReconsultationPreview(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping(value = "/{customerId}/consultations", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "재상담 등록", description = "multipart/form-data로 기존 고객의 새 상담 회차, 누적 상담 정보, 등록 상태를 저장합니다. request part에는 재상담 등록 JSON을, materials part에는 선택 상담자료 .txt 파일을 최대 3개까지 전달합니다. AI 중간 점검은 첨부자료를 받지 않습니다.")
    public ResponseEntity<ApiResponse<ReconsultationCreateResponse>> createReconsultation(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @Parameter(description = "재상담 등록 요청 JSON part", required = true)
            @Valid @RequestPart("request") ReconsultationCreateRequest request,
            @Parameter(description = "선택 상담자료 파일 part. .txt만 허용하며 최대 3개, 파일당 1MB까지 지원합니다.")
            @RequestPart(value = "materials", required = false) List<MultipartFile> materials
    ) {
        ReconsultationCreateResponse response = customerService.createReconsultation(
                storeId,
                customerId,
                request,
                materials
        );
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/{customerId}/ai-analysis")
    @Operation(summary = "AI 분석값 수동 수정", description = "AI 상담요약, 고객온도, 주요 이탈요인을 수동 수정합니다. 다음 최적 액션은 자동 변경하지 않습니다.")
    public ResponseEntity<ApiResponse<CustomerAiAnalysisUpdateResponse>> updateAiAnalysis(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @Valid @RequestBody CustomerAiAnalysisUpdateRequest request
    ) {
        CustomerAiAnalysisUpdateResponse response = customerService.updateAiAnalysis(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/{customerId}/next-action/regenerate")
    @Operation(summary = "다음 최적 액션 재생성", description = "최신 고객/상담/AI 분석값을 기준으로 다음 최적 액션을 수동 재생성합니다.")
    public ResponseEntity<ApiResponse<NextActionRegenerateResponse>> regenerateNextAction(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @RequestBody NextActionRegenerateRequest request
    ) {
        NextActionRegenerateResponse response = customerService.regenerateNextAction(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PatchMapping("/{customerId}/status")
    @Operation(summary = "고객 상태 변경", description = "고객 상태를 변경하고 REGISTERED/LOST 전환 시 후속관리를 종료합니다. REGISTERED 전환이면 후속 전환 귀속을 중복 없이 저장합니다.")
    public ResponseEntity<ApiResponse<CustomerStatusUpdateResponse>> updateCustomerStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Customer ID") @PathVariable UUID customerId,
            @Valid @RequestBody CustomerStatusUpdateRequest request
    ) {
        CustomerStatusUpdateResponse response = customerService.updateCustomerStatus(storeId, customerId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
