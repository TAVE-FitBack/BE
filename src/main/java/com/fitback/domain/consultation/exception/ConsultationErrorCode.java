package com.fitback.domain.consultation.exception;

import com.fitback.global.exception.BaseErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum ConsultationErrorCode implements BaseErrorCode {

    SERVICE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 서비스를 찾을 수 없습니다."
    ),
    INFLOW_PATH_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "선택한 방문 경로를 찾을 수 없습니다."
    ),
    CUSTOMER_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "고객 정보를 찾을 수 없습니다."
    ),
    CONSULTATION_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상담 정보를 찾을 수 없습니다."
    ),
    COUNSELOR_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "상담자를 찾을 수 없습니다."
    ),
    STORE_NOT_ASSIGNED(
            HttpStatus.BAD_REQUEST,
            "사용자에게 연결된 매장이 없습니다."
    ),
    DUPLICATE_CUSTOMER_PHONE(
            HttpStatus.CONFLICT,
            "이미 등록된 연락처의 고객이 존재합니다."
    ),
    AI_CHECK_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 중간 점검 중 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
    ),
    AI_ANALYSIS_REQUEST_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 분석 서버에 연결할 수 없습니다. 잠시 후 다시 시도해주세요."
    ),
    AI_ANALYSIS_RESPONSE_INVALID(
            HttpStatus.BAD_GATEWAY,
            "AI 분석 응답 형식이 올바르지 않습니다."
    ),
    AI_ANALYSIS_FAILED(
            HttpStatus.BAD_GATEWAY,
            "AI 분석 중 오류가 발생했습니다."
    ),
    AI_ANALYSIS_SAVE_FAILED(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "AI 분석 결과 저장 중 오류가 발생했습니다."
    ),
    CONSULTATION_MATERIAL_FILE_COUNT_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "상담자료 첨부파일은 요청당 최대 3개까지 등록할 수 있습니다."
    ),
    CONSULTATION_MATERIAL_FILE_SIZE_EXCEEDED(
            HttpStatus.BAD_REQUEST,
            "상담자료 첨부파일은 파일당 최대 1MB까지 등록할 수 있습니다."
    ),
    CONSULTATION_MATERIAL_UNSUPPORTED_FILE_TYPE(
            HttpStatus.BAD_REQUEST,
            "상담자료 첨부파일은 .txt 파일만 등록할 수 있습니다."
    ),
    CONSULTATION_MATERIAL_ENCODING_UNSUPPORTED(
            HttpStatus.BAD_REQUEST,
            "상담자료 첨부파일 인코딩은 UTF-8 또는 MS949만 지원합니다."
    ),
    CONSULTATION_MATERIAL_CONTENT_TOO_LONG(
            HttpStatus.BAD_REQUEST,
            "상담자료 첨부파일의 전체 텍스트 길이는 요청당 최대 30,000자까지 등록할 수 있습니다."
    );

    private final HttpStatus httpStatus;
    private final String message;
}
