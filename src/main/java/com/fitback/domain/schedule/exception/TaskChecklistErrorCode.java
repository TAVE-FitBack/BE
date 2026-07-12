package com.fitback.domain.schedule.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum TaskChecklistErrorCode implements BaseErrorCode {

    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    TASK_CHECKLIST_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "체크리스트를 찾을 수 없습니다."
    ),
    INVALID_INPUT_VALUE(
            HttpStatus.BAD_REQUEST,
            "입력값이 올바르지 않습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
