package com.pharmaprice.common.exception;

import org.springframework.http.HttpStatus;

/**
 * 공통 에러 코드. 도메인별 세부 코드(PHARMACY_NOT_FOUND 등)는 해당 도메인 태스크에서 추가한다.
 * API.md §1.3 표의 공통분모만 우선 정의한다.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "요청 값이 올바르지 않습니다."),
    INVALID_COORDINATE(HttpStatus.BAD_REQUEST, "INVALID_COORDINATE", "위경도가 대한민국 범위를 벗어났습니다."),
    INVALID_RADIUS(HttpStatus.BAD_REQUEST, "INVALID_RADIUS", "반경은 500/1000/2000/5000 중 하나여야 합니다."),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "NOT_FOUND", "리소스를 찾을 수 없습니다."),
    DRUG_NOT_FOUND(HttpStatus.NOT_FOUND, "DRUG_NOT_FOUND", "의약품을 찾을 수 없습니다."),
    PHARMACY_NOT_FOUND(HttpStatus.NOT_FOUND, "PHARMACY_NOT_FOUND", "약국을 찾을 수 없습니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "REPORT_NOT_FOUND", "제보를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "EMAIL_ALREADY_EXISTS", "이미 가입된 이메일입니다."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "구매일은 오늘 이전 180일 이내여야 합니다."),
    DRUG_NOT_OTC(HttpStatus.UNPROCESSABLE_ENTITY, "DRUG_NOT_OTC", "일반의약품이 아닌 약품은 제보할 수 없습니다."),
    DUPLICATE_REPORT(HttpStatus.CONFLICT, "DUPLICATE_REPORT", "오늘 이미 같은 약국·약품에 대한 제보가 있습니다."),
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "파일 크기는 5MB를 초과할 수 없습니다."),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", "jpg/png/webp 이미지만 업로드할 수 있습니다."),
    CONFLICT(HttpStatus.CONFLICT, "CONFLICT", "이미 존재하거나 충돌하는 요청입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "서버 내부 오류가 발생했습니다.");

    private final HttpStatus httpStatus;
    private final String code;
    private final String message;

    ErrorCode(HttpStatus httpStatus, String code, String message) {
        this.httpStatus = httpStatus;
        this.code = code;
        this.message = message;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }
}
