package com.fitback.domain.store.controller;

import com.fitback.domain.store.dto.request.StoreSetupRequest;
import com.fitback.domain.store.dto.response.StoreSetupResponse;
import com.fitback.domain.store.service.StoreService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/store")
@RequiredArgsConstructor
@Tag(name = "Store", description = "매장 API")
public class StoreController {

    private final StoreService storeService;

    /* 매장 설정 */
    @PostMapping
    @Operation(summary = "매장 초기 설정", description = "최초 로그인 시 매장 정보 설정")
    public ResponseEntity<ApiResponse<StoreSetupResponse>> setup(
            @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody StoreSetupRequest request
    ) {
        StoreSetupResponse response = storeService.setup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("매장이 등록되었습니다.", response));
    }
}