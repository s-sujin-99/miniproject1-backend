package com.pharmaprice.pharmacy.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * 약국 검색/상세의 조인 쿼리(ROADMAP T-19). 목록은 q 필터만 SQL에서 처리하고, lat/lng가 있을 때의 반경
 * 판정은 서비스가 DistanceCalculator.distanceMeters로 앱단에서 한다(Haversine 재구현 금지, T-09 재사용).
 *
 * ponytail: 반경 필터를 SQL이 아니라 candidate 전체를 읽어 앱단에서 거른다 — 시드 규모(수백 건)에서는
 * 충분하지만 약국 수가 늘어나면 SearchQueryRepository처럼 바운딩박스 사전 필터를 추가해야 한다.
 */
@Repository
public class PharmacyQueryRepository {

    private static final String CANDIDATES_SQL = """
            SELECT p.id, p.name, p.address_road, p.lat, p.lng, p.phone, r.code, r.sido, r.sigungu
            FROM pharmacy p
            LEFT JOIN region r ON r.code = p.region_code
            WHERE p.is_active = true
              AND (CAST(:q AS text) IS NULL OR p.name ILIKE '%' || :q || '%' OR p.address_road ILIKE '%' || :q || '%')
            ORDER BY p.name ASC, p.id ASC
            """;

    private static final String DRUG_PRICES_SQL = """
            SELECT d.id, d.display_name, d.package_unit,
                   s.rep_price, s.min_price, s.max_price, s.avg_price, s.report_count, s.last_reported_at,
                   nat.national_avg
            FROM pharmacy_drug_price_stat s
            JOIN drug d ON d.id = s.drug_id
            LEFT JOIN (
                SELECT drug_id, AVG(rep_price)::int AS national_avg
                FROM pharmacy_drug_price_stat
                GROUP BY drug_id
            ) nat ON nat.drug_id = d.id
            WHERE s.pharmacy_id = :pharmacyId
            ORDER BY s.rep_price ASC, d.id ASC
            """;

    private final EntityManager entityManager;

    public PharmacyQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<PharmacyRow> findCandidates(String q) {
        Query query = entityManager.createNativeQuery(CANDIDATES_SQL).setParameter("q", q);
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toRow).toList();
    }

    @SuppressWarnings("unchecked")
    public List<DrugPriceResponse> findDrugPrices(long pharmacyId) {
        Query query = entityManager.createNativeQuery(DRUG_PRICES_SQL).setParameter("pharmacyId", pharmacyId);
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toDrugPrice).toList();
    }

    private PharmacyRow toRow(Object[] row) {
        return new PharmacyRow(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).doubleValue(),
                ((Number) row[4]).doubleValue(),
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (String) row[8]);
    }

    private DrugPriceResponse toDrugPrice(Object[] row) {
        int repPrice = ((Number) row[3]).intValue();
        Integer nationalAvg = row[9] == null ? null : ((Number) row[9]).intValue();
        return new DrugPriceResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                repPrice,
                ((Number) row[4]).intValue(),
                ((Number) row[5]).intValue(),
                ((Number) row[6]).intValue(),
                ((Number) row[7]).intValue(),
                toLocalDate(row[8]),
                nationalAvg,
                nationalAvg == null ? null : repPrice - nationalAvg);
    }

    // Hibernate 버전에 따라 DATE 컬럼이 LocalDate 또는 java.sql.Date로 매핑된다(SearchQueryRepository와 동일 이슈).
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((java.sql.Date) value).toLocalDate();
    }

    public record PharmacyRow(long id, String name, String addressRoad, double lat, double lng, String phone,
                               String regionCode, String regionSido, String regionSigungu) {
    }
}
