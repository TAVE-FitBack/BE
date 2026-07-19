package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.FollowUpReplyUpdateRequest;
import com.fitback.domain.customer.dto.response.FollowUpReplyUpdateResponse;
import com.fitback.domain.customer.service.CustomerService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/follow-ups")
@RequiredArgsConstructor
@Tag(name = "FollowUp", description = "후속 연락 API")
public class FollowUpController {

    private final CustomerService customerService;

    @PatchMapping("/{followUpId}/reply")
    @Operation(summary = "답장 유무 변경", description = "후속 연락의 답장 유무를 수동으로 변경합니다.")
    public ResponseEntity<ApiResponse<FollowUpReplyUpdateResponse>> updateReply(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(description = "Follow-up ID") @PathVariable UUID followUpId,
            @Valid @RequestBody FollowUpReplyUpdateRequest request
    ) {
        FollowUpReplyUpdateResponse response = customerService.updateFollowUpReply(storeId, followUpId, request);
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
