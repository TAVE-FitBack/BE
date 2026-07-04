package com.fitback.domain.consultation.client;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
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
public class AiConsultationClient {

    private static final String CHECK_PREVIEW_PATH = "/ai/v1/consultations/check-preview";

    private final RestClient restClient;

    public AiConsultationClient(@Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(aiBaseUrl)
                .build();
    }

    public Map<String, Object> checkPreview(AiCheckPreviewRequest request) {
        try {
            return restClient.post()
                    .uri(CHECK_PREVIEW_PATH)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (RestClientException e) {
            log.warn("AI check-preview request failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_CHECK_FAILED);
        }
    }
}
