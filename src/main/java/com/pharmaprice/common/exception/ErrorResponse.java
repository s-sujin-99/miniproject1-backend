package com.pharmaprice.common.exception;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

/**
 * API.md §1.2 에러 응답 포맷. fieldErrors는 검증 실패일 때만 채운다.
 */
public record ErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> fieldErrors,
        String traceId,
        OffsetDateTime timestamp
) {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    public static ErrorResponse of(ErrorCode errorCode) {
        return of(errorCode, errorCode.getMessage(), null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return of(errorCode, message, null);
    }

    public static ErrorResponse of(ErrorCode errorCode, String message, List<FieldErrorDetail> fieldErrors) {
        // traceId는 요청 추적용 짧은 식별자. 별도 트레이싱 도입 전까지는 랜덤 UUID 앞 8자리로 대신한다.
        String traceId = UUID.randomUUID().toString().substring(0, 8);
        return new ErrorResponse(errorCode.getCode(), message, fieldErrors, traceId, OffsetDateTime.now(KST));
    }

    public record FieldErrorDetail(String field, String reason) {
    }
}
