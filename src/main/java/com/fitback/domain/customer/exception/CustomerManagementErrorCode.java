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
            "Store is not assigned to the current user."
    ),
    INVALID_MONTH_FORMAT(
            HttpStatus.BAD_REQUEST,
            "Month must be in YYYY-MM format."
    ),
    INVALID_SEARCH_CONDITION(
            HttpStatus.BAD_REQUEST,
            "Search condition is invalid."
    ),
    INVALID_FILTER_CONDITION(
            HttpStatus.BAD_REQUEST,
            "Filter condition is invalid."
    ),
    INVALID_MANAGEMENT_STAGE(
            HttpStatus.BAD_REQUEST,
            "Unsupported management stage filter."
    ),
    INVALID_PAGE_REQUEST(
            HttpStatus.BAD_REQUEST,
            "Page request is invalid."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
