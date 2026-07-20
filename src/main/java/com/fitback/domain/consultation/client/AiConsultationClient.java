package com.fitback.domain.consultation.client;

import com.fitback.domain.consultation.dto.request.AiCheckPreviewRequest;
import com.fitback.domain.consultation.dto.request.AiConsultationAnalyzeRequest;
import com.fitback.domain.consultation.dto.request.AiConsultationGraphSyncRequest;
import com.fitback.domain.consultation.dto.request.AiNextActionRegenerateRequest;
import com.fitback.domain.consultation.dto.response.AiConsultationAnalyzeResponse;
import com.fitback.domain.consultation.dto.response.AiNextActionRegenerateResponse;
import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Component
public class AiConsultationClient {

    private static final String CHECK_PREVIEW_PATH = "/ai/v1/consultations/check-preview";
    private static final String ANALYZE_PATH = "/ai/v1/consultations/analyze";
    private static final String NEXT_ACTION_PATH = "/ai/v1/consultations/next-action";
    private static final String GRAPH_SYNC_PATH = "/ai/v1/graph/consultations/sync";

    private final RestClient restClient;

    public AiConsultationClient(
            @Value("${ai.base-url:http://localhost:8000}") String aiBaseUrl,
            @Value("${ai.connect-timeout-ms:3000}") long connectTimeoutMs,
            @Value("${ai.read-timeout-ms:30000}") long readTimeoutMs
    ) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));

        this.restClient = RestClient.builder()
                .baseUrl(aiBaseUrl)
                .requestFactory(requestFactory)
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

    public AiConsultationAnalyzeResponse analyzeConsultation(AiConsultationAnalyzeRequest request) {
        try {
            AiConsultationAnalyzeResponse response = restClient.post()
                    .uri(ANALYZE_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiConsultationAnalyzeResponse.class);
            validateAnalyzeResponse(response);
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI consultation analyze request failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_REQUEST_FAILED);
        } catch (RestClientResponseException e) {
            log.warn("AI consultation analyze returned error status. status={}", e.getStatusCode(), e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpMessageConversionException e) {
            log.warn("AI consultation analyze response parsing failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AI consultation analyze response invalid", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        }
    }

    public AiNextActionRegenerateResponse regenerateNextAction(AiNextActionRegenerateRequest request) {
        try {
            AiNextActionRegenerateResponse response = restClient.post()
                    .uri(NEXT_ACTION_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiNextActionRegenerateResponse.class);
            validateNextActionResponse(response);
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI next-action request failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_REQUEST_FAILED);
        } catch (RestClientResponseException e) {
            log.warn("AI next-action returned error status. status={}", e.getStatusCode(), e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpMessageConversionException e) {
            log.warn("AI next-action response parsing failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AI next-action response invalid", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        }
    }

    public Map<String, Object> syncConsultationGraph(AiConsultationGraphSyncRequest request) {
        try {
            return restClient.post()
                    .uri(GRAPH_SYNC_PATH)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
        } catch (ResourceAccessException e) {
            log.warn("AI consultation graph sync request failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_REQUEST_FAILED);
        } catch (RestClientResponseException e) {
            log.warn("AI consultation graph sync returned error status. status={}", e.getStatusCode(), e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_FAILED);
        } catch (HttpMessageConversionException e) {
            log.warn("AI consultation graph sync response parsing failed", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AI consultation graph sync response invalid", e);
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        }
    }

    private void validateAnalyzeResponse(AiConsultationAnalyzeResponse response) {
        if (response == null
                || !hasText(response.getSummary())
                || response.getCustomerInsight() == null
                || !hasText(response.getCustomerInsight().getLeadTemperature())
                || response.getNextBestAction() == null
                || !hasText(response.getNextBestAction().getTitle())
                || !hasText(response.getNextBestAction().getDescription())
                || response.getFollowUp() == null
                || response.getFollowUp().getRecommendContactDate() == null) {
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        }
    }

    private void validateNextActionResponse(AiNextActionRegenerateResponse response) {
        if (response == null
                || response.getPriorityScore() == null
                || response.getNextBestAction() == null
                || !hasText(response.getNextBestAction().getTitle())
                || !hasText(response.getNextBestAction().getDescription())
                || response.getFollowUp() == null
                || response.getFollowUp().getRecommendContactDate() == null) {
            throw new BusinessException(ConsultationErrorCode.AI_ANALYSIS_RESPONSE_INVALID);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
