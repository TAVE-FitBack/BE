package com.fitback.domain.customer.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum FollowUpManagementErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    ),
    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "시작일은 종료일보다 늦을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
