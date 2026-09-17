package com.pharmaprice.common.exception;

/**
 * 서비스 레이어에서 던지는 표준 예외. 컨트롤러는 이 예외를 잡지 않고
 * GlobalExceptionHandler가 일괄 변환한다(shrimp-rules.md §4.5).
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
