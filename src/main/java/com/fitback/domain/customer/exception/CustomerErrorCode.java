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
    ),
    ACTIVE_FOLLOW_UP_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "진행 중인 후속 연락을 찾을 수 없습니다."
    ),
    FOLLOW_UP_NOT_PENDING(
            HttpStatus.BAD_REQUEST,
            "후속 연락이 대기 상태가 아닙니다."
    ),
    NEXT_ACTION_NOT_FOUND(
            HttpStatus.BAD_REQUEST,
            "다음 최적 액션을 찾을 수 없습니다."
    ),
    EVENT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "이벤트를 찾을 수 없습니다."
    ),
    MESSAGE_TEMPLATE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "메시지 초안을 찾을 수 없습니다."
    ),
    MESSAGE_GENERATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "메시지 생성에 실패했습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
