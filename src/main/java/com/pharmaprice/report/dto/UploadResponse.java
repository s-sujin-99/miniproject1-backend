package com.pharmaprice.report.dto;

import java.time.OffsetDateTime;

import com.pharmaprice.report.domain.UploadedFile;

/** API.md §6 POST /api/v1/uploads 응답. */
public record UploadResponse(
        Long id,
        String originalName,
        String contentType,
        long sizeBytes,
        String url,
        OffsetDateTime createdAt) {

    public static UploadResponse from(UploadedFile file) {
        return new UploadResponse(
                file.getId(),
                file.getOriginalName(),
                file.getContentType(),
                file.getSizeBytes(),
                "/api/v1/uploads/" + file.getId(),
                file.getCreatedAt());
    }
}
