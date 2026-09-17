package com.pharmaprice.report.dto;

/** GET /api/v1/uploads/{fileId} 응답 바디 구성에 쓰는 값. */
public record DownloadedFile(byte[] content, String contentType, String originalName) {
}
