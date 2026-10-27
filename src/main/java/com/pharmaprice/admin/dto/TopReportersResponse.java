package com.pharmaprice.admin.dto;

import java.util.List;

/** API.md §8 GET /api/v1/admin/stats/top-reporters 응답. */
public record TopReportersResponse(List<Row> rows) {

    public record Row(Reporter reporter, int reportCount) {
    }

    public record Reporter(long id, String nickname, String email) {
    }
}
