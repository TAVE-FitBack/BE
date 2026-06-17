package com.fitback.domain.auth.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements BaseErrorCode {

    EMAIL_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 사용 중인 이메일입니다."
    ),
    NICKNAME_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "이미 사용 중인 닉네임입니다."
    ),
    PASSWORD_NOT_MATCH(
            HttpStatus.BAD_REQUEST,
            "비밀번호가 일치하지 않습니다."
    ),
    TERMS_NOT_AGREED(
            HttpStatus.BAD_REQUEST,
            "서비스 이용약관에 동의해주세요."
    ),
    INVALID_CREDENTIALS(
            HttpStatus.UNAUTHORIZED,
            "이메일 또는 비밀번호가 올바르지 않습니다."
    ),
    EMAIL_NOT_VERIFIED(
            HttpStatus.FORBIDDEN,
            "이메일 인증이 필요합니다."
    ),
    INVALID_VERIFICATION_TOKEN(
            HttpStatus.BAD_REQUEST,
            "유효하지 않은 인증 토큰입니다."
    ),
    INVALID_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "유효하지 않은 Refresh Token입니다."
    ),
    EXPIRED_REFRESH_TOKEN(
            HttpStatus.UNAUTHORIZED,
            "만료된 Refresh Token입니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}