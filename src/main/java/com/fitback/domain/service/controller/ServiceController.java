package com.fitback.domain.service.controller;

import com.fitback.domain.service.dto.request.ServiceCreateRequest;
import com.fitback.domain.service.dto.request.ServiceUpdateRequest;
import com.fitback.domain.service.dto.response.ServiceResponse;
import com.fitback.domain.service.service.ServiceService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/store/services")
@RequiredArgsConstructor
@Tag(name = "Service", description = "매장 서비스 상품 API")
public class ServiceController {

    private final ServiceService serviceService;

    @GetMapping
    @Operation(summary = "서비스 목록 조회")
    public ResponseEntity<ApiResponse<List<ServiceResponse>>> getServices(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(serviceService.getServices(userId)));
    }

    @PostMapping
    @Operation(summary = "서비스 등록")
    public ResponseEntity<ApiResponse<ServiceResponse>> createService(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody ServiceCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        "서비스가 등록되었습니다.",
                        serviceService.createService(userId, request)
                ));
    }

    @PutMapping("/{serviceId}")
    @Operation(summary = "서비스 수정")
    public ResponseEntity<ApiResponse<ServiceResponse>> updateService(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Service ID") @PathVariable UUID serviceId,
            @Valid @RequestBody ServiceUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(
                "서비스가 수정되었습니다.",
                serviceService.updateService(userId, serviceId, request)
        ));
    }

    @DeleteMapping("/{serviceId}")
    @Operation(summary = "서비스 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteService(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Service ID") @PathVariable UUID serviceId
    ) {
        serviceService.deleteService(userId, serviceId);
        return ResponseEntity.ok(ApiResponse.onSuccess("서비스가 삭제되었습니다.", null));
    }
}
