package com.pharmaprice.common.dto;

import java.util.List;

/** shrimp-rules §4.2 — Spring {@code Page}를 그대로 반환하지 않고 이 래퍼로 감싼다. */
public record PageResponse<T>(List<T> content, int page, int size,
                               long totalElements, int totalPages, boolean hasNext) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        boolean hasNext = (long) (page + 1) * size < totalElements;
        return new PageResponse<>(content, page, size, totalElements, totalPages, hasNext);
    }
}
