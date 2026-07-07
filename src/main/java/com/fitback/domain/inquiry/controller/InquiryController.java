package com.fitback.domain.inquiry.controller;

import com.fitback.domain.inquiry.dto.request.InquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.service.InquiryService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/inquiries")
@RequiredArgsConstructor
@Tag(name = "Inquiry", description = "문의 API")
public class InquiryController {

    private final InquiryService inquiryService;

    @GetMapping("/new")
    @Operation(summary = "문의 등록 초기 데이터 조회", description = "로그인 사용자의 매장 기준으로 활성 서비스 목록, 문의 경로 옵션 목록, 상담자 목록, 문의 상태 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<InquiryNewResponse>> getNewInquiryData(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId
    ) {
        InquiryNewResponse response = inquiryService.getNewInquiryData(storeId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }

    @PostMapping("/check-preview")
    @Operation(summary = "문의 내용 AI 중간 점검", description = "문의 원문과 고객 기본 정보를 AI 서버에 전달해 작성 가이드라인 충족 여부를 점검합니다.")
    public ResponseEntity<ApiResponse<Map<String, Object>>> checkPreview(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Valid @RequestBody InquiryCheckPreviewRequest request
    ) {
        Map<String, Object> response = inquiryService.checkPreview(storeId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
