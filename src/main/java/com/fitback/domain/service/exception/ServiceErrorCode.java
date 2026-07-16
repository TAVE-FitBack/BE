package com.fitback.domain.service.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ServiceErrorCode implements BaseErrorCode {

    SERVICE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "서비스를 찾을 수 없습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}