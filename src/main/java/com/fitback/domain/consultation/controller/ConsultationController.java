package com.fitback.domain.consultation.controller;

import com.fitback.domain.consultation.dto.response.ConsultationNewResponse;
import com.fitback.domain.consultation.service.ConsultationService;
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
@RequestMapping("/api/consultations")
@RequiredArgsConstructor
@Tag(name = "Consultation", description = "상담 API")
public class ConsultationController {

    private final ConsultationService consultationService;

    @GetMapping("/new")
    @Operation(summary = "상담 등록 초기 데이터 조회", description = "로그인 사용자의 매장 기준으로 활성 서비스 목록과 상담자 목록을 조회합니다.")
    public ResponseEntity<ApiResponse<ConsultationNewResponse>> getNewConsultationData(
            @AuthenticationPrincipal(expression = "user.storeId") UUID storeId
    ) {
        ConsultationNewResponse response = consultationService.getNewConsultationData(storeId);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
