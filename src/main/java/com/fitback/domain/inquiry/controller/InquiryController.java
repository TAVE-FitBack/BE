package com.fitback.domain.inquiry.controller;

import com.fitback.domain.inquiry.dto.response.InquiryNewResponse;
import com.fitback.domain.inquiry.service.InquiryService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
