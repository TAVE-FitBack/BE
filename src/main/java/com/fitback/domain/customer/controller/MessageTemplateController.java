package com.fitback.domain.customer.controller;

import com.fitback.domain.customer.dto.request.MessageTemplateMarkSentRequest;
import com.fitback.domain.customer.dto.response.MessageTemplateMarkSentResponse;
import com.fitback.domain.customer.service.CustomerService;
import com.fitback.global.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/message-templates")
@RequiredArgsConstructor
@Tag(name = "MessageTemplate", description = "메시지 초안 API")
public class MessageTemplateController {

    private final CustomerService customerService;

    @PostMapping("/{messageTemplateId}/mark-sent")
    @Operation(summary = "메시지 전송 완료", description = "사용자가 외부 채널로 메시지 전송을 완료한 뒤 메시지와 후속 연락을 완료 처리합니다.")
    public ResponseEntity<ApiResponse<MessageTemplateMarkSentResponse>> markMessageTemplateSent(
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.storeId") UUID storeId,
            @Parameter(hidden = true) @AuthenticationPrincipal(expression = "user.id") UUID userId,
            @Parameter(description = "Message template ID") @PathVariable UUID messageTemplateId,
            @RequestBody(required = false) MessageTemplateMarkSentRequest request
    ) {
        MessageTemplateMarkSentResponse response = customerService.markMessageTemplateSent(
                storeId,
                userId,
                messageTemplateId,
                request
        );
        return ResponseEntity.ok(ApiResponse.onSuccess(response));
    }
}
