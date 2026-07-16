package com.fitback.domain.event.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum EventErrorCode implements BaseErrorCode {

    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "이벤트를 찾을 수 없습니다."
    ),

    INVALID_EVENT_DATE(
            HttpStatus.BAD_REQUEST,
        "종료일은 시작일보다 이후여야 합니다."
    ),
    ;

    private final HttpStatus httpStatus;
    private final String message;
}