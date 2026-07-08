package com.fitback.domain.customer.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomerErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "고객을 찾을 수 없습니다."
    ),
    CUSTOMER_ACCESS_DENIED(
            HttpStatus.FORBIDDEN,
            "해당 고객에 접근할 수 없습니다."
    ),
    INVALID_CUSTOMER_STATUS(
            HttpStatus.BAD_REQUEST,
            "허용되지 않는 고객 상태입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
