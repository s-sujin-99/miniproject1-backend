package com.pharmaprice.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Score 가중치·창 크기(API.md §5)를 설정 파일에서 주입받는다. 튜닝 시 코드 재배포 없이
 * application.properties만 바꾸면 되도록 하드코딩을 금지한다(shrimp-rules §4.6).
 */
@ConfigurationProperties(prefix = "recommendation")
public record RecommendationProperties(
        Weights weights,
        int freshnessHalfLifeDays,
        int priceWindowDays,
        int priceWindowFallbackDays,
        Outlier outlier
) {
    public record Weights(double price, double distance, double freshness) {
    }

    public record Outlier(double iqrMultiplier, int minSamples) {
    }
}
