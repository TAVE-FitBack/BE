package com.fitback.domain.customer.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum CustomerManagementErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    INVALID_MONTH_FORMAT(
            HttpStatus.BAD_REQUEST,
            "조회 월은 YYYY-MM 형식이어야 합니다."
    ),
    INVALID_SEARCH_CONDITION(
            HttpStatus.BAD_REQUEST,
            "검색 조건이 올바르지 않습니다."
    ),
    INVALID_FILTER_CONDITION(
            HttpStatus.BAD_REQUEST,
            "필터 조건이 올바르지 않습니다."
    ),
    INVALID_PAGE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "페이지 요청이 올바르지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
