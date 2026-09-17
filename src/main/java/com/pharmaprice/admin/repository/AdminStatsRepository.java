package com.pharmaprice.admin.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

/**
 * 관리자 통계 집계 쿼리(ROADMAP T-31, API.md §8, DATABASE.md §5.3/§5.4). 단일 엔티티에 매이지 않는
 * 집계라 PharmacyQueryRepository(T-19)와 같은 방식으로 EntityManager 네이티브 쿼리를 직접 쓴다.
 *
 * 표본이 너무 적은 지역이 "최저가 지역"처럼 보이면 안 되므로, 지역 단위 평균은 전부
 * 그 지역에 표본(약국) 3건 미만이면 제외한다(DATABASE.md §5.4의 HAVING COUNT(*) >= 3와 동일 기준).
 */
@Repository
public class AdminStatsRepository {

    private static final String TOTALS_SQL = """
            SELECT
              (SELECT COUNT(*) FROM pharmacy WHERE is_active = true) AS pharmacy_count,
              (SELECT COUNT(*) FROM drug WHERE otc_flag = true) AS drug_count,
              (SELECT COUNT(*) FROM price_report WHERE deleted_at IS NULL) AS report_count,
              (SELECT COUNT(*) FROM app_user WHERE deleted_at IS NULL) AS user_count,
              (SELECT COUNT(*) FROM pharmacy_drug_price_stat) AS covered_pair_count
            """;

    // 최근 7일(오늘 포함) 제보 추이 — 데이터가 없는 날도 0으로 채워야 차트가 끊기지 않는다.
    private static final String RECENT_TREND_SQL = """
            SELECT gs::date AS day, COALESCE(t.cnt, 0) AS report_count
            FROM generate_series(CURRENT_DATE - 6, CURRENT_DATE, interval '1 day') gs
            LEFT JOIN (
                SELECT (created_at AT TIME ZONE 'Asia/Seoul')::date AS report_date, COUNT(*) AS cnt
                FROM price_report
                WHERE deleted_at IS NULL
                GROUP BY report_date
            ) t ON t.report_date = gs::date
            ORDER BY gs
            """;

    private static final String FLAGGED_COUNT_SQL =
            "SELECT COUNT(*) FROM price_report WHERE flagged = true AND deleted_at IS NULL";

    private static final String REGION_STATS_SQL = """
            SELECT r.code, r.sido, r.sigungu, d.id, d.display_name,
                   ROUND(AVG(s.rep_price))::int AS avg_price,
                   MIN(s.rep_price) AS min_price,
                   MAX(s.rep_price) AS max_price,
                   COUNT(DISTINCT s.pharmacy_id) AS pharmacy_count,
                   SUM(s.report_count) AS report_count
            FROM pharmacy_drug_price_stat s
            JOIN pharmacy p ON p.id = s.pharmacy_id
            JOIN region   r ON r.code = p.region_code
            JOIN drug     d ON d.id = s.drug_id
            WHERE (CAST(:regionCode AS text) IS NULL OR p.region_code = :regionCode)
              AND (CAST(:drugId AS bigint) IS NULL OR s.drug_id = CAST(:drugId AS bigint))
              AND (CAST(:sido AS text) IS NULL OR r.sido = :sido)
            GROUP BY r.code, r.sido, r.sigungu, d.id, d.display_name
            HAVING COUNT(DISTINCT s.pharmacy_id) >= 3
            ORDER BY r.sido, r.sigungu, d.display_name
            """;

    // 500원 버킷 히스토그램.
    private static final String DRUG_DISTRIBUTION_SQL = """
            SELECT (FLOOR(rep_price / 500.0) * 500)::int AS bucket_from, COUNT(*) AS cnt
            FROM pharmacy_drug_price_stat
            WHERE drug_id = :drugId
            GROUP BY bucket_from
            ORDER BY bucket_from
            """;

    private static final String DRUG_BY_REGION_SQL = """
            SELECT r.sido, r.sigungu, ROUND(AVG(s.rep_price))::int AS avg_price, COUNT(*) AS pharmacy_count
            FROM pharmacy_drug_price_stat s
            JOIN pharmacy p ON p.id = s.pharmacy_id
            JOIN region   r ON r.code = p.region_code
            WHERE s.drug_id = :drugId
            GROUP BY r.sido, r.sigungu
            HAVING COUNT(*) >= 3
            ORDER BY avg_price ASC
            """;

    private static final String DRUG_NATIONAL_SQL = """
            SELECT ROUND(AVG(rep_price))::int,
                   ROUND(percentile_cont(0.5) WITHIN GROUP (ORDER BY rep_price))::int,
                   MIN(rep_price), MAX(rep_price),
                   ROUND(STDDEV_POP(rep_price))::int
            FROM pharmacy_drug_price_stat
            WHERE drug_id = :drugId
            """;

    // 지역 간 가격 격차(price-gaps)의 원자료 — 드럭×지역별 평균가. 최종 top-N 선정/격차 계산은
    // 서비스에서 한다(어느 지역이 최저/최고인지 식별하려면 SQL만으로는 DISTINCT ON 등이 더 복잡해진다).
    private static final String PRICE_GAP_SOURCE_SQL = """
            SELECT s.drug_id, d.display_name, r.sido, r.sigungu, AVG(s.rep_price) AS region_avg
            FROM pharmacy_drug_price_stat s
            JOIN pharmacy p ON p.id = s.pharmacy_id
            JOIN region   r ON r.code = p.region_code
            JOIN drug     d ON d.id = s.drug_id
            GROUP BY s.drug_id, d.display_name, r.sido, r.sigungu
            HAVING COUNT(*) >= 3
            ORDER BY s.drug_id
            """;

    private final EntityManager entityManager;

    public AdminStatsRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public TotalsRow findTotals() {
        Object[] row = (Object[]) entityManager.createNativeQuery(TOTALS_SQL).getSingleResult();
        return new TotalsRow(toInt(row[0]), toInt(row[1]), toInt(row[2]), toInt(row[3]), toInt(row[4]));
    }

    @SuppressWarnings("unchecked")
    public List<TrendRow> findRecentTrend() {
        List<Object[]> rows = entityManager.createNativeQuery(RECENT_TREND_SQL).getResultList();
        return rows.stream().map(r -> new TrendRow(toLocalDate(r[0]), toInt(r[1]))).toList();
    }

    public long countFlaggedReports() {
        return ((Number) entityManager.createNativeQuery(FLAGGED_COUNT_SQL).getSingleResult()).longValue();
    }

    @SuppressWarnings("unchecked")
    public List<RegionStatRow> findRegionStats(String regionCode, Long drugId, String sido) {
        Query query = entityManager.createNativeQuery(REGION_STATS_SQL)
                .setParameter("regionCode", regionCode)
                .setParameter("drugId", drugId)
                .setParameter("sido", sido);
        List<Object[]> rows = query.getResultList();
        return rows.stream().map(this::toRegionStatRow).toList();
    }

    @SuppressWarnings("unchecked")
    public List<BucketRow> findDrugDistribution(long drugId) {
        List<Object[]> rows = entityManager.createNativeQuery(DRUG_DISTRIBUTION_SQL)
                .setParameter("drugId", drugId).getResultList();
        return rows.stream().map(r -> new BucketRow(toInt(r[0]), toInt(r[1]))).toList();
    }

    @SuppressWarnings("unchecked")
    public List<RegionAvgRow> findDrugByRegion(long drugId) {
        List<Object[]> rows = entityManager.createNativeQuery(DRUG_BY_REGION_SQL)
                .setParameter("drugId", drugId).getResultList();
        return rows.stream()
                .map(r -> new RegionAvgRow((String) r[0], (String) r[1], toInt(r[2]), toInt(r[3])))
                .toList();
    }

    public NationalStatRow findDrugNationalStat(long drugId) {
        Object[] row = (Object[]) entityManager.createNativeQuery(DRUG_NATIONAL_SQL)
                .setParameter("drugId", drugId).getSingleResult();
        if (row[0] == null) {
            return null;
        }
        return new NationalStatRow(toInt(row[0]), toInt(row[1]), toInt(row[2]), toInt(row[3]), toInt(row[4]));
    }

    @SuppressWarnings("unchecked")
    public List<PriceGapSourceRow> findPriceGapSource() {
        List<Object[]> rows = entityManager.createNativeQuery(PRICE_GAP_SOURCE_SQL).getResultList();
        return rows.stream()
                .map(r -> new PriceGapSourceRow(((Number) r[0]).longValue(), (String) r[1],
                        (String) r[2], (String) r[3], ((Number) r[4]).doubleValue()))
                .toList();
    }

    private RegionStatRow toRegionStatRow(Object[] row) {
        return new RegionStatRow(
                (String) row[0], (String) row[1], (String) row[2],
                ((Number) row[3]).longValue(), (String) row[4],
                toInt(row[5]), toInt(row[6]), toInt(row[7]), toInt(row[8]), toInt(row[9]));
    }

    private int toInt(Object value) {
        return ((Number) value).intValue();
    }

    // Hibernate 버전에 따라 DATE 컬럼이 LocalDate 또는 java.sql.Date로 매핑된다(PharmacyQueryRepository와 동일 이슈).
    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        return ((java.sql.Date) value).toLocalDate();
    }

    public record TotalsRow(int pharmacyCount, int drugCount, int reportCount, int userCount, int coveredPairCount) {
    }

    public record TrendRow(LocalDate date, int reportCount) {
    }

    public record RegionStatRow(String regionCode, String sido, String sigungu, long drugId, String drugDisplayName,
                                 int avgPrice, int minPrice, int maxPrice, int pharmacyCount, int reportCount) {
    }

    public record BucketRow(int bucketFrom, int count) {
    }

    public record RegionAvgRow(String sido, String sigungu, int avgPrice, int pharmacyCount) {
    }

    public record NationalStatRow(int avg, int median, int min, int max, int stdDev) {
    }

    public record PriceGapSourceRow(long drugId, String drugDisplayName, String sido, String sigungu, double regionAvg) {
    }
}
