package com.fitback.domain.store.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum StoreErrorCode implements BaseErrorCode {

    STORE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "매장을 찾을 수 없습니다."
    ),
    STORE_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 매장이 등록되어 있습니다."
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}