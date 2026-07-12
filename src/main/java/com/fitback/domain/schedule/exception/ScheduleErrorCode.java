package com.fitback.domain.schedule.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ScheduleErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    USER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "사용자를 찾을 수 없습니다."
    ),
    SCHEDULE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "일정을 찾을 수 없습니다."
    ),
    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    ),
    INVALID_DATE_RANGE(
            HttpStatus.BAD_REQUEST,
            "일정 시간 범위가 올바르지 않습니다."
    ),
    INVALID_SCHEDULE_TYPE(
            HttpStatus.BAD_REQUEST,
            "허용되지 않은 일정 유형입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
