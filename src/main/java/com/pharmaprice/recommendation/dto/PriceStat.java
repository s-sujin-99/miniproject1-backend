package com.pharmaprice.recommendation.dto;

import java.time.LocalDate;

/** PriceStatService.recalculate()의 결과값(DATABASE.md §5.2). */
public record PriceStat(
        int repPrice,
        int minPrice,
        int maxPrice,
        int avgPrice,
        int reportCount,
        LocalDate lastReportedAt,
        short windowDays
) {
}
