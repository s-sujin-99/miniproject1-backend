package com.pharmaprice.common.exception;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.slf4j.MDC;

import com.pharmaprice.common.web.TraceIdFilter;

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
        // TraceIdFilter가 MDC에 심어둔 값과 같은 traceId를 써야 로그 검색이 된다. 필터를 거치지 않는
        // 컨텍스트(테스트 등)를 대비해 없으면 새로 하나 발급한다.
        String traceId = MDC.get(TraceIdFilter.TRACE_ID_KEY);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().substring(0, 8);
        }
        return new ErrorResponse(errorCode.getCode(), message, fieldErrors, traceId, OffsetDateTime.now(KST));
    }

    public record FieldErrorDetail(String field, String reason) {
    }
}
