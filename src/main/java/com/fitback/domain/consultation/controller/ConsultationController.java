package com.fitback.domain.consultation.controller;

import com.fitback.domain.consultation.dto.request.ConsultationCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.ConsultationCreateRequest;
import com.fitback.domain.consultation.dto.response.ConsultationCreateResponse;
import com.fitback.domain.consultation.dto.response.ConsultationCustomerSearchResponse;
import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.service.ConsultationService;
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
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
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

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "상담 등록", description = "multipart/form-data로 신규 고객 최초 상담을 저장합니다. request part에는 상담 등록 JSON을, materials part에는 선택 상담자료 .txt 파일을 최대 3개까지 전달합니다. AI 중간 점검은 첨부자료를 받지 않습니다.")
    public ResponseEntity<ApiResponse<ConsultationCreateResponse>> createConsultation(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "상담 등록 요청 JSON part", required = true)
            @Valid @RequestPart("request") ConsultationCreateRequest request,
            @Parameter(description = "선택 상담자료 파일 part. .txt만 허용하며 최대 3개, 파일당 1MB까지 지원합니다.")
            @RequestPart(value = "materials", required = false) List<MultipartFile> materials
    ) {
        ConsultationCreateResponse response = consultationService.createConsultation(storeId, request, materials);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(response));
    }
}
