package com.fitback.domain.analysisreport.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AnalysisReportErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    INVALID_MONTH_FORMAT(
            HttpStatus.BAD_REQUEST,
            "조회 월 형식이 올바르지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
