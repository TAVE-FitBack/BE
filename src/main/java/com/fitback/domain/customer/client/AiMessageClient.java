package com.fitback.domain.customer.client;

import com.fitback.domain.customer.dto.request.AiMessageGenerateRequest;
import com.fitback.domain.customer.dto.response.AiMessageGenerateResponse;
import com.fitback.domain.customer.exception.CustomerErrorCode;
import com.fitback.global.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.converter.HttpMessageConversionException;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;

@Slf4j
@Component
public class AiMessageClient {

    private static final String GENERATE_PATH = "/ai/v1/messages/generate";

    private final RestClient restClient;

    public AiMessageClient(
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

    public AiMessageGenerateResponse generateMessage(AiMessageGenerateRequest request) {
        try {
            AiMessageGenerateResponse response = restClient.post()
                    .uri(GENERATE_PATH)
                    .body(request)
                    .retrieve()
                    .body(AiMessageGenerateResponse.class);
            validateGenerateResponse(response);
            return response;
        } catch (ResourceAccessException e) {
            log.warn("AI message generate request failed", e);
            throw new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED);
        } catch (RestClientResponseException e) {
            log.warn("AI message generate returned error status. status={}", e.getStatusCode(), e);
            throw new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED);
        } catch (BusinessException e) {
            throw e;
        } catch (HttpMessageConversionException e) {
            log.warn("AI message generate response parsing failed", e);
            throw new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("AI message generate response invalid", e);
            throw new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED);
        }
    }

    private void validateGenerateResponse(AiMessageGenerateResponse response) {
        if (response == null || !hasText(response.getContent())) {
            throw new BusinessException(CustomerErrorCode.MESSAGE_GENERATION_FAILED);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
