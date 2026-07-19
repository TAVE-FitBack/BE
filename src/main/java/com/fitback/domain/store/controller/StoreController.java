package com.fitback.domain.store.controller;

import com.fitback.domain.store.dto.request.InflowPathCreateRequest;
import com.fitback.domain.store.dto.request.InflowPathUpdateRequest;
import com.fitback.domain.store.dto.request.StoreSetupRequest;
import com.fitback.domain.store.dto.response.InflowPathResponse;
import com.fitback.domain.store.dto.response.StoreSetupResponse;
import com.fitback.domain.store.service.InflowPathService;
import com.fitback.domain.store.service.StoreService;
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
@RequestMapping("/api/store")
@RequiredArgsConstructor
@Tag(name = "Store", description = "매장 API")
public class StoreController {

    private final StoreService storeService;
    private final InflowPathService inflowPathService;

    /* 매장 설정 */
    @PostMapping
    @Operation(summary = "매장 초기 설정", description = "최초 로그인 시 매장 정보 설정")
    public ResponseEntity<ApiResponse<StoreSetupResponse>> setup(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody StoreSetupRequest request
    ) {
        StoreSetupResponse response = storeService.setup(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess("매장이 등록되었습니다.", response));
    }

    @GetMapping("/inflow-paths")
    @Operation(summary = "매장 방문 경로 목록 조회")
    public ResponseEntity<ApiResponse<List<InflowPathResponse>>> getInflowPaths(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId
    ) {
        return ResponseEntity.ok(ApiResponse.onSuccess(inflowPathService.getInflowPaths(userId)));
    }

    @PostMapping("/inflow-paths")
    @Operation(summary = "매장 방문 경로 등록")
    public ResponseEntity<ApiResponse<InflowPathResponse>> createInflowPath(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Valid @RequestBody InflowPathCreateRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.onSuccess(
                        "유입 경로가 등록되었습니다.",
                        inflowPathService.createInflowPath(userId, request)
                ));
    }

    @PutMapping("/inflow-paths/{inflowPathId}")
    @Operation(summary = "매장 방문 경로 수정")
    public ResponseEntity<ApiResponse<InflowPathResponse>> updateInflowPath(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Inflow path ID") @PathVariable UUID inflowPathId,
            @Valid @RequestBody InflowPathUpdateRequest request
    ) {
        return ResponseEntity.ok(
                ApiResponse.onSuccess(
                        "유입 경로가 수정되었습니다.",
                        inflowPathService.updateInflowPath(userId, inflowPathId, request)
                ));
    }

    @DeleteMapping("/inflow-paths/{inflowPathId}")
    @Operation(summary = "매장 방문 경로 삭제")
    public ResponseEntity<ApiResponse<Void>> deleteInflowPath(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Inflow path ID") @PathVariable UUID inflowPathId
    ) {
        inflowPathService.deleteInflowPath(userId, inflowPathId);
        return ResponseEntity.ok(ApiResponse.onSuccess("방문 경로가 삭제되었습니다.", null));
    }
}
