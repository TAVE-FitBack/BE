package com.fitback.domain.inquiry.client;

import com.fitback.domain.inquiry.dto.request.AiInquiryCheckPreviewRequest;
import com.fitback.domain.inquiry.exception.InquiryErrorCode;
import com.fitback.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

@Slf4j
@Component
public class AiInquiryClient {

    private static final String CHECK_PREVIEW_PATH = "/ai/v1/inquiries/check-preview";

    private final RestClient restClient;

    public AiInquiryClient(@Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(aiBaseUrl)
                .build();
    }

    public Map<String, Object> checkPreview(AiInquiryCheckPreviewRequest request) {
        try {
            return restClient.post()
                    .uri(CHECK_PREVIEW_PATH)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException e) {
            log.warn("AI inquiry check-preview request failed", e);
            throw new BusinessException(InquiryErrorCode.AI_CHECK_FAILED);
        }
    }
}
