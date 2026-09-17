package com.pharmaprice.report.repository;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;

public interface PriceReportRepository extends JpaRepository<PriceReport, Long> {

    /** GET /api/v1/search의 dataSource(SEED/MIXED/USER) 판정용 — 결과에 포함된 약국들의 제보 출처 종류. */
    @Query("""
            SELECT DISTINCT pr.source FROM PriceReport pr
            WHERE pr.drug.id = :drugId AND pr.pharmacy.id IN :pharmacyIds
              AND pr.status = :status AND pr.flagged = false
            """)
    List<ReportSource> findDistinctSources(@Param("drugId") long drugId, @Param("pharmacyIds") List<Long> pharmacyIds,
            @Param("status") ReportStatus status);

    /**
     * 가격 이력(ROADMAP T-20, API.md §4 history). flagged=true인 제보도 그대로 포함해야
     * 차트가 이상치를 표시할 수 있다 — PriceStatServiceImpl의 통계용 쿼리(flagged 제외)와는 목적이 다르다.
     */
    @Query("""
            SELECT pr.purchasedAt, pr.price, pr.flagged FROM PriceReport pr
            WHERE pr.pharmacy.id = :pharmacyId AND pr.drug.id = :drugId
              AND pr.status = :status AND pr.purchasedAt >= :fromDate
            ORDER BY pr.purchasedAt ASC
            """)
    List<Object[]> findHistory(@Param("pharmacyId") long pharmacyId, @Param("drugId") long drugId,
            @Param("status") ReportStatus status, @Param("fromDate") LocalDate fromDate);

    /** F2-9 이상치 판정 기준 — 약국을 가리지 않고 이 약품 전체의 유효 제보 가격 중앙값. 제보가 없으면 null. */
    @Query(value = """
            SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY price)
            FROM price_report
            WHERE drug_id = :drugId AND status = 'ACTIVE' AND flagged = false AND deleted_at IS NULL
            """, nativeQuery = true)
    Double findMedianPriceByDrugId(@Param("drugId") long drugId);

    /**
     * GET /api/v1/price-reports 목록(ROADMAP T-28, T-30) 및 GET /api/v1/admin/price-reports(T-32) 공용.
     * 네 필터 모두 선택값 — null이면 조건을 건너뛴다.
     * status는 공개 목록(mine=false)에서만 ACTIVE로 고정해서 넘긴다 — mine=true(/me)와 관리자 목록은 null을
     * 넘겨 HIDDEN 제보도 그대로 보여준다(shrimp-rules §"토글 API" — HIDDEN은 삭제가 아니라 노출 상태).
     */
    @Query("""
            SELECT pr FROM PriceReport pr
            JOIN FETCH pr.pharmacy
            JOIN FETCH pr.drug
            LEFT JOIN FETCH pr.user
            LEFT JOIN FETCH pr.receiptFile
            WHERE (:status IS NULL OR pr.status = :status)
              AND (:pharmacyId IS NULL OR pr.pharmacy.id = :pharmacyId)
              AND (:drugId IS NULL OR pr.drug.id = :drugId)
              AND (:userId IS NULL OR pr.user.id = :userId)
              AND (:flagged IS NULL OR pr.flagged = :flagged)
            ORDER BY pr.createdAt DESC
            """)
    Page<PriceReport> search(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
            @Param("userId") Long userId, @Param("status") ReportStatus status, @Param("flagged") Boolean flagged,
            Pageable pageable);
}
