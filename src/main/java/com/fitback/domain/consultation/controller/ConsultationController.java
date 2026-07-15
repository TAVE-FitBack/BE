package com.fitback.domain.consultation.controller;

import com.fitback.domain.consultation.dto.request.ConsultationCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.service.ConsultationService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
@Tag(name = "Consultation", description = "상담 API")
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping("/new")
    @Operation(summary = "상담 등록 초기 데이터 조회", description = "로그인 사용자의 매장 기준으로 활성 서비스 목록, 방문경로 옵션 목록, 상담자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ConsultationNewResponse>> getNewConsultationData(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId
    ) {
        ConsultationNewResponse response = consultationService.getNewConsultationData(storeId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @GetMapping("/search")
    @Operation(summary = "연락처 기반 기존 고객 검색", description = "로그인 사용자의 매장 기준으로 연락처가 일치하는 기존 고객을 조회합니다.")
    public ResponseEntity<ApiResponse<ConsultationCustomerSearchResponse>> searchCustomerByPhone(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @RequestParam String phone
    ) {
        ConsultationCustomerSearchResponse response = consultationService.searchCustomerByPhone(storeId, phone);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/check-preview")
    @Operation(summary = "상담 내용 AI 중간 점검", description = "상담 원문과 고객 기본 정보를 AI 서버에 전달해 작성 가이드라인 충족 여부를 점검합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPreview(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Valid @RequestBody ConsultationCheckPreviewRequest request
    ) {
        Map<String, Object> response = consultationService.checkPreview(storeId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping
    @Operation(summary = "상담 등록", description = "신규 고객 최초 상담을 저장합니다. 등록 완료 상태이면 등록 서비스와 등록 시각을 저장하고 후속 전환 귀속을 중복 없이 저장합니다.")
    public ResponseEntity<ApiResponse<ConsultationCreateResponse>> createConsultation(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Valid @RequestBody ConsultationCreateRequest request
    ) {
        ConsultationCreateResponse response = consultationService.createConsultation(storeId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }
}
