package com.fitback.domain.consultation.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsultationErrorCode implements BaseErrorCode {

    SERVICE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 서비스를 찾을 수 없습니다."
    ),
    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "고객 정보를 찾을 수 없습니다."
    ),
    COUNSELOR_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상담자를 찾을 수 없습니다."
    ),
    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    DUPLICATE_CUSTOMER_PHONE(
            HttpStatus.CONFLICT,
            "이미 등록된 연락처의 고객이 존재합니다."
    ),
    AI_CHECK_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 중간 점검 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
