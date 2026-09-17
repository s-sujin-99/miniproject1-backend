package com.pharmaprice.recommendation.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.pharmaprice.recommendation.dto.BoundingBox;
import com.pharmaprice.recommendation.dto.PharmacyPriceCandidate;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * GET /api/v1/search의 1차 사각형 필터 쿼리(ROADMAP T-15). pharmacy_drug_price_stat과 pharmacy를
 * 조인해 활성 약국만 가져오고, 정확한 원형 반경 판정은 호출 측이 DistanceCalculator로 2차 필터링한다.
 */
@Repository
public class SearchQueryRepository {

    private static final String CANDIDATES_SQL = """
            SELECT p.id, p.name, p.address_road, p.lat, p.lng, p.phone,
                   s.rep_price, s.min_price, s.avg_price, s.report_count, s.last_reported_at
            FROM pharmacy_drug_price_stat s
            JOIN pharmacy p ON p.id = s.pharmacy_id
            WHERE s.drug_id = :drugId
              AND p.is_active = true
              AND p.lat BETWEEN :minLat AND :maxLat
              AND p.lng BETWEEN :minLng AND :maxLng
            """;

    private final EntityManager entityManager;

    public SearchQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<PharmacyPriceCandidate> findWithinBoundingBox(long drugId, BoundingBox box) {
        Query query = entityManager.createNativeQuery(CANDIDATES_SQL)
                .setParameter("drugId", drugId)
                .setParameter("minLat", box.minLat())
                .setParameter("maxLat", box.maxLat())
                .setParameter("minLng", box.minLng())
                .setParameter("maxLng", box.maxLng());
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toCandidate).toList();
    }

    private PharmacyPriceCandidate toCandidate(Object[] row) {
        return new PharmacyPriceCandidate(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).doubleValue(),
                ((Number) row[4]).doubleValue(),
                (String) row[5],
                ((Number) row[6]).intValue(),
                ((Number) row[7]).intValue(),
                ((Number) row[8]).intValue(),
                ((Number) row[9]).intValue(),
                toLocalDate(row[10]));
    }

    // Hibernate 버전에 따라 DATE 컬럼이 LocalDate 또는 java.sql.Date로 매핑되므로 둘 다 받아준다(PriceStatServiceImpl과 동일 이슈).
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((java.sql.Date) value).toLocalDate();
    }
}
