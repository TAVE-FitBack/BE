package com.fitback.domain.inquiry.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum InquiryErrorCode implements BaseErrorCode {

    INQUIRY_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "문의를 찾을 수 없습니다."
    ),
    INQUIRY_ALREADY_CONVERTED(
            HttpStatus.BAD_REQUEST,
            "이미 상담으로 전환된 문의입니다."
    ),
    CUSTOMER_DUPLICATE_CONFLICT(
            HttpStatus.CONFLICT,
            "동일한 연락처의 고객이 동시에 생성되어 충돌이 발생했습니다."
    ),
    CONSULTATION_CREATE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "문의 전환 상담을 생성하지 못했습니다."
    ),
    SERVICE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 서비스를 찾을 수 없습니다."
    ),
    INFLOW_PATH_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 문의 경로를 찾을 수 없습니다."
    ),
    COUNSELOR_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상담자를 찾을 수 없습니다."
    ),
    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    AI_CHECK_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 중간 점검 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
