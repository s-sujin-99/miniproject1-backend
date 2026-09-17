package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.common.config.RecommendationProperties;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.domain.PharmacyDrugPriceStat;
import com.pharmaprice.recommendation.dto.PriceStat;
import com.pharmaprice.recommendation.repository.PharmacyDrugPriceStatRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * DATABASE.md §5.2 절차(90일 창 → 0건이면 180일 확대 → IQR 제거 → 중앙값)를 그대로 구현한다.
 * SQL은 JPQL로 표현할 수 없는 percentile_cont를 쓰므로 네이티브 쿼리를 쓴다.
 */
@Service
@RequiredArgsConstructor
public class PriceStatServiceImpl implements PriceStatService {

    private static final String STATS_SQL = """
            WITH valid AS (
                SELECT price
                FROM price_report
                WHERE pharmacy_id = :pharmacyId
                  AND drug_id = :drugId
                  AND status = 'ACTIVE'
                  AND flagged = false
                  AND deleted_at IS NULL
                  AND purchased_at >= CURRENT_DATE - :windowDays
            ),
            q AS (
                SELECT
                    percentile_cont(0.25) WITHIN GROUP (ORDER BY price) AS q1,
                    percentile_cont(0.75) WITHIN GROUP (ORDER BY price) AS q3,
                    count(*) AS n
                FROM valid
            ),
            trimmed AS (
                SELECT v.price
                FROM valid v CROSS JOIN q
                WHERE q.n < :minSamples
                   OR v.price BETWEEN q.q1 - :iqrMultiplier * (q.q3 - q.q1)
                                  AND q.q3 + :iqrMultiplier * (q.q3 - q.q1)
            )
            SELECT
                percentile_cont(0.5) WITHIN GROUP (ORDER BY price)::int AS rep_price,
                MIN(price)::int AS min_price,
                MAX(price)::int AS max_price,
                AVG(price)::int AS avg_price,
                COUNT(*)::int AS report_count
            FROM trimmed
            """;

    private static final String LAST_REPORTED_AT_SQL = """
            SELECT MAX(purchased_at)
            FROM price_report
            WHERE pharmacy_id = :pharmacyId
              AND drug_id = :drugId
              AND status = 'ACTIVE'
              AND flagged = false
              AND deleted_at IS NULL
              AND purchased_at >= CURRENT_DATE - :windowDays
            """;

    private final EntityManager entityManager;
    private final PharmacyDrugPriceStatRepository statRepository;
    private final PharmacyRepository pharmacyRepository;
    private final DrugRepository drugRepository;
    private final RecommendationProperties properties;

    @Override
    @Transactional
    public Optional<PriceStat> recalculate(long pharmacyId, long drugId) {
        Object[] row = fetchStats(pharmacyId, drugId, properties.priceWindowDays());
        int reportCount = row[4] == null ? 0 : ((Number) row[4]).intValue();
        short windowDays = (short) properties.priceWindowDays();

        if (reportCount == 0) {
            row = fetchStats(pharmacyId, drugId, properties.priceWindowFallbackDays());
            reportCount = row[4] == null ? 0 : ((Number) row[4]).intValue();
            windowDays = (short) properties.priceWindowFallbackDays();
        }

        Optional<PharmacyDrugPriceStat> existing = statRepository.findByPharmacyIdAndDrugId(pharmacyId, drugId);
        if (reportCount == 0) {
            existing.ifPresent(statRepository::delete);
            return Optional.empty();
        }

        int repPrice = ((Number) row[0]).intValue();
        int minPrice = ((Number) row[1]).intValue();
        int maxPrice = ((Number) row[2]).intValue();
        int avgPrice = ((Number) row[3]).intValue();
        LocalDate lastReportedAt = fetchLastReportedAt(pharmacyId, drugId, windowDays);
        OffsetDateTime calculatedAt = OffsetDateTime.now();

        if (existing.isPresent()) {
            existing.get().applyStats(repPrice, minPrice, maxPrice, avgPrice, reportCount,
                    lastReportedAt, windowDays, calculatedAt);
        } else {
            statRepository.save(PharmacyDrugPriceStat.builder()
                    .pharmacy(pharmacyRepository.getReferenceById(pharmacyId))
                    .drug(drugRepository.getReferenceById(drugId))
                    .repPrice(repPrice).minPrice(minPrice).maxPrice(maxPrice).avgPrice(avgPrice)
                    .reportCount(reportCount).lastReportedAt(lastReportedAt)
                    .windowDays(windowDays).calculatedAt(calculatedAt)
                    .build());
        }

        return Optional.of(new PriceStat(repPrice, minPrice, maxPrice, avgPrice, reportCount, lastReportedAt, windowDays));
    }

    private Object[] fetchStats(long pharmacyId, long drugId, int windowDays) {
        Query query = entityManager.createNativeQuery(STATS_SQL)
                .setParameter("pharmacyId", pharmacyId)
                .setParameter("drugId", drugId)
                .setParameter("windowDays", windowDays)
                .setParameter("minSamples", properties.outlier().minSamples())
                .setParameter("iqrMultiplier", properties.outlier().iqrMultiplier());
        return (Object[]) query.getSingleResult();
    }

    private LocalDate fetchLastReportedAt(long pharmacyId, long drugId, int windowDays) {
        Object result = entityManager.createNativeQuery(LAST_REPORTED_AT_SQL)
                .setParameter("pharmacyId", pharmacyId)
                .setParameter("drugId", drugId)
                .setParameter("windowDays", windowDays)
                .getSingleResult();
        // Hibernate 버전에 따라 DATE 컬럼이 LocalDate 또는 java.sql.Date로 매핑되므로 둘 다 받아준다.
        if (result instanceof LocalDate localDate) {
            return localDate;
        }
        return ((java.sql.Date) result).toLocalDate();
    }
}
