package com.pharmaprice.drug.repository;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

import com.pharmaprice.drug.dto.DrugDetailResponse;
import com.pharmaprice.drug.dto.DrugSummaryResponse;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * 의약품 검색 목록·상세의 가격 통계 조인을 네이티브 쿼리로 처리한다(N+1 방지, ROADMAP T-13).
 * pharmacy_drug_price_stat을 서브쿼리로 집계 후 LEFT JOIN — 가격 정보 없는 약품도 결과에 포함되어야 하기 때문.
 */
@Repository
public class DrugQueryRepository {

    private static final String SEARCH_SQL = """
            SELECT d.id, d.item_seq, d.display_name, d.name, d.maker, d.category, d.form, d.package_unit, d.image_url,
                   s.avg_price, s.pharmacy_count
            FROM drug d
            LEFT JOIN (
                SELECT drug_id, AVG(rep_price)::int AS avg_price, COUNT(*)::int AS pharmacy_count
                FROM pharmacy_drug_price_stat
                GROUP BY drug_id
            ) s ON s.drug_id = d.id
            WHERE d.otc_flag = true
              AND (CAST(:q AS text) IS NULL OR d.display_name ILIKE '%' || :q || '%' OR d.name ILIKE '%' || :q || '%')
              AND (CAST(:category AS text) IS NULL OR d.category = :category)
            ORDER BY d.display_name ASC, d.id ASC
            LIMIT :limit OFFSET :offset
            """;

    private static final String COUNT_SQL = """
            SELECT COUNT(*)
            FROM drug d
            WHERE d.otc_flag = true
              AND (CAST(:q AS text) IS NULL OR d.display_name ILIKE '%' || :q || '%' OR d.name ILIKE '%' || :q || '%')
              AND (CAST(:category AS text) IS NULL OR d.category = :category)
            """;

    private static final String DETAIL_STATS_SQL = """
            SELECT AVG(rep_price)::int, MIN(rep_price)::int, MAX(rep_price)::int,
                   COUNT(*)::int, COALESCE(SUM(report_count), 0)::int
            FROM pharmacy_drug_price_stat
            WHERE drug_id = :drugId
            """;

    private final EntityManager entityManager;

    public DrugQueryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @SuppressWarnings("unchecked")
    public List<DrugSummaryResponse> search(String q, String category, Pageable pageable) {
        Query query = entityManager.createNativeQuery(SEARCH_SQL)
                .setParameter("q", q)
                .setParameter("category", category)
                .setParameter("limit", pageable.getPageSize())
                .setParameter("offset", pageable.getOffset());
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toSummary).toList();
    }

    public long count(String q, String category) {
        Query query = entityManager.createNativeQuery(COUNT_SQL)
                .setParameter("q", q)
                .setParameter("category", category);
        return ((Number) query.getSingleResult()).longValue();
    }

    public DrugDetailResponse.PriceStats findStats(long drugId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(DETAIL_STATS_SQL)
                .setParameter("drugId", drugId)
                .getSingleResult();
        return new DrugDetailResponse.PriceStats(
                (Integer) row[0], (Integer) row[1], (Integer) row[2],
                ((Number) row[3]).intValue(), ((Number) row[4]).intValue());
    }

    private DrugSummaryResponse toSummary(Object[] row) {
        return new DrugSummaryResponse(
                ((Number) row[0]).longValue(),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                (String) row[4],
                (String) row[5],
                (String) row[6],
                (String) row[7],
                (String) row[8],
                row[9] == null ? null : ((Number) row[9]).intValue(),
                row[10] == null ? 0 : ((Number) row[10]).intValue());
    }
}
