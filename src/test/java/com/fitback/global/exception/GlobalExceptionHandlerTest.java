package com.fitback.global.exception;

import com.fitback.domain.consultation.exception.ConsultationErrorCode;
import com.fitback.global.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("필수값 누락 validation 실패는 400 INVALID_INPUT_VALUE를 반환한다")
    void validationException() {
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        BindingResult bindingResult = mock(BindingResult.class);
        when(exception.getBindingResult()).thenReturn(bindingResult);
        when(bindingResult.getFieldErrors()).thenReturn(List.of(
                new FieldError("consultationCreateRequest", "customer.phoneNum", "연락처는 필수입니다.")
        ));

        ResponseEntity<ApiResponse<Map<String, String>>> response = handler.handleValidationException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.name());
        assertThat(response.getBody().data()).containsEntry("customer.phoneNum", "연락처는 필수입니다.");
    }

    @Test
    @DisplayName("JSON 파싱 또는 enum 오류는 400 INVALID_INPUT_VALUE를 반환한다")
    void httpMessageNotReadableException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleHttpMessageNotReadableException(
                mock(HttpMessageNotReadableException.class)
        );

        assertInvalidInput(response);
    }

    @Test
    @DisplayName("필수 query parameter 누락은 400 INVALID_INPUT_VALUE를 반환한다")
    void missingRequestParameterException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMissingServletRequestParameterException(
                new MissingServletRequestParameterException("phone", "String")
        );

        assertInvalidInput(response);
    }

    @Test
    @DisplayName("request parameter type mismatch returns 400 INVALID_INPUT_VALUE")
    void methodArgumentTypeMismatchException() {
        ResponseEntity<ApiResponse<Void>> response = handler.handleMethodArgumentTypeMismatchException(
                new MethodArgumentTypeMismatchException("invalid", Long.class, "serviceId", null, null)
        );

        assertInvalidInput(response);
    }

    @Test
    @DisplayName("매장 없음은 400 STORE_NOT_ASSIGNED를 반환한다")
    void storeNotAssigned() {
        assertBusinessError(ConsultationErrorCode.STORE_NOT_ASSIGNED, HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("서비스 없음/비활성/타매장은 404 SERVICE_NOT_FOUND를 반환한다")
    void serviceNotFound() {
        assertBusinessError(ConsultationErrorCode.SERVICE_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("방문경로 없음/비활성/타매장은 404 INFLOW_PATH_NOT_FOUND를 반환한다")
    void inflowPathNotFound() {
        assertBusinessError(ConsultationErrorCode.INFLOW_PATH_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("상담자 없음은 404 COUNSELOR_NOT_FOUND를 반환한다")
    void counselorNotFound() {
        assertBusinessError(ConsultationErrorCode.COUNSELOR_NOT_FOUND, HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("연락처 중복은 409 DUPLICATE_CUSTOMER_PHONE을 반환한다")
    void duplicateCustomerPhone() {
        assertBusinessError(ConsultationErrorCode.DUPLICATE_CUSTOMER_PHONE, HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("AI 실패는 502 AI_CHECK_FAILED를 반환한다")
    void aiCheckFailed() {
        assertBusinessError(ConsultationErrorCode.AI_CHECK_FAILED, HttpStatus.BAD_GATEWAY);
    }

    private void assertBusinessError(ConsultationErrorCode errorCode, HttpStatus status) {
        ResponseEntity<ApiResponse<Void>> response = handler.handleBusinessException(new BusinessException(errorCode));

        assertThat(response.getStatusCode()).isEqualTo(status);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(errorCode.name());
        assertThat(response.getBody().message()).isEqualTo(errorCode.getMessage());
    }

    private void assertInvalidInput(ResponseEntity<ApiResponse<Void>> response) {
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().success()).isFalse();
        assertThat(response.getBody().code()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.name());
        assertThat(response.getBody().message()).isEqualTo(ErrorCode.INVALID_INPUT_VALUE.getMessage());
    }
}
