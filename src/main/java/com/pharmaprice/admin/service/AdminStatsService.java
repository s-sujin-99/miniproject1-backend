package com.pharmaprice.admin.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.admin.dto.DrugStatsResponse;
import com.pharmaprice.admin.dto.OverviewResponse;
import com.pharmaprice.admin.dto.PriceGapsResponse;
import com.pharmaprice.admin.dto.RegionStatsResponse;
import com.pharmaprice.admin.repository.AdminStatsRepository;
import com.pharmaprice.admin.repository.AdminStatsRepository.NationalStatRow;
import com.pharmaprice.admin.repository.AdminStatsRepository.PriceGapSourceRow;
import com.pharmaprice.admin.repository.AdminStatsRepository.TotalsRow;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;

import lombok.RequiredArgsConstructor;

/**
 * API.md §8 관리자 통계(ROADMAP T-31). 표본 3건 미만인 지역/약품은 통계에서 제외한다
 * (AdminStatsRepository의 각 쿼리가 HAVING COUNT(*) >= 3로 이미 걸러서 넘긴다).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminStatsService {

    // 500원 단위 히스토그램(API.md §8 drugs/{drugId} 예시와 동일).
    private static final int BUCKET_SIZE = 500;
    // 지역 간 가격 격차 비교는 최소 3개 지역의 평균가가 있어야 의미가 있다(DATABASE.md §5.4).
    private static final int MIN_REGIONS_FOR_GAP = 3;

    private final AdminStatsRepository repository;
    private final DrugRepository drugRepository;

    public OverviewResponse overview() {
        TotalsRow totals = repository.findTotals();
        List<OverviewResponse.TrendPoint> trend = repository.findRecentTrend().stream()
                .map(r -> new OverviewResponse.TrendPoint(r.date(), r.reportCount()))
                .toList();
        long flaggedCount = repository.countFlaggedReports();
        long possiblePairs = (long) totals.pharmacyCount() * totals.drugCount();
        double coverageRate = possiblePairs == 0 ? 0.0
                : Math.round((double) totals.coveredPairCount() / possiblePairs * 100) / 100.0;

        return new OverviewResponse(
                new OverviewResponse.Totals(totals.pharmacyCount(), totals.drugCount(), totals.reportCount(),
                        totals.userCount(), totals.coveredPairCount()),
                trend, flaggedCount, coverageRate);
    }

    public RegionStatsResponse regions(String regionCode, Long drugId, String sido) {
        List<RegionStatsResponse.Row> rows = repository.findRegionStats(regionCode, drugId, sido).stream()
                .map(r -> new RegionStatsResponse.Row(
                        new RegionStatsResponse.RegionRef(r.regionCode(), r.sido(), r.sigungu()),
                        new RegionStatsResponse.DrugRef(r.drugId(), r.drugDisplayName()),
                        r.avgPrice(), r.minPrice(), r.maxPrice(), r.pharmacyCount(), r.reportCount()))
                .toList();
        return new RegionStatsResponse(rows);
    }

    public DrugStatsResponse drugStats(long drugId) {
        Drug drug = drugRepository.findById(drugId).orElseThrow(() -> new BusinessException(ErrorCode.DRUG_NOT_FOUND));

        List<DrugStatsResponse.Bucket> distribution = repository.findDrugDistribution(drugId).stream()
                .map(b -> new DrugStatsResponse.Bucket(b.bucketFrom(), b.bucketFrom() + BUCKET_SIZE, b.count()))
                .toList();
        List<DrugStatsResponse.RegionAvg> byRegion = repository.findDrugByRegion(drugId).stream()
                .map(r -> new DrugStatsResponse.RegionAvg(r.sido(), r.sigungu(), r.avgPrice(), r.pharmacyCount()))
                .toList();
        NationalStatRow nationalRow = repository.findDrugNationalStat(drugId);
        // 표본이 아예 없는(가격 제보/통계가 없는) 약품이면 national은 null — 프론트가 "데이터 없음"으로 표시한다.
        DrugStatsResponse.National national = nationalRow == null ? null
                : new DrugStatsResponse.National(nationalRow.avg(), nationalRow.median(), nationalRow.min(),
                        nationalRow.max(), nationalRow.stdDev());

        return new DrugStatsResponse(
                new DrugStatsResponse.DrugRef(drug.getId(), drug.getDisplayName(), drug.getPackageUnit()),
                distribution, byRegion, national);
    }

    public PriceGapsResponse priceGaps(int limit) {
        // groupingBy 기본 구현은 HashMap이라 약품 순서가 흐트러진다 — 재현 가능한 결과를 위해 SQL의
        // ORDER BY s.drug_id 순서를 LinkedHashMap으로 유지한다(최종 결과는 어차피 gapPct로 다시 정렬한다).
        Map<Long, List<PriceGapSourceRow>> byDrug = repository.findPriceGapSource().stream()
                .collect(Collectors.groupingBy(PriceGapSourceRow::drugId, LinkedHashMap::new, Collectors.toList()));

        List<PriceGapsResponse.Row> rows = new ArrayList<>();
        for (List<PriceGapSourceRow> group : byDrug.values()) {
            // 지역이 3곳 미만이면 "격차"라는 비교 자체가 성립하지 않는다(DATABASE.md §5.4 outer HAVING).
            if (group.size() < MIN_REGIONS_FOR_GAP) continue;

            PriceGapSourceRow cheapest = group.stream().min(Comparator.comparingDouble(PriceGapSourceRow::regionAvg)).orElseThrow();
            PriceGapSourceRow priciest = group.stream().max(Comparator.comparingDouble(PriceGapSourceRow::regionAvg)).orElseThrow();
            // gap/gapPct는 응답에 실제로 보이는 반올림된 avgPrice 두 값으로 계산한다 — 원본(반올림 전) 평균으로
            // 계산하면 화면에 찍힌 두 avgPrice로 수기 검산했을 때 gapPct가 미세하게 안 맞는 경우가 생긴다.
            int cheapestAvg = (int) Math.round(cheapest.regionAvg());
            int priciestAvg = (int) Math.round(priciest.regionAvg());
            int gap = priciestAvg - cheapestAvg;
            double gapPct = Math.round(gap / (double) cheapestAvg * 1000) / 10.0;

            rows.add(new PriceGapsResponse.Row(
                    new PriceGapsResponse.DrugRef(cheapest.drugId(), cheapest.drugDisplayName()),
                    new PriceGapsResponse.RegionRef(cheapest.sido(), cheapest.sigungu(), cheapestAvg),
                    new PriceGapsResponse.RegionRef(priciest.sido(), priciest.sigungu(), priciestAvg),
                    gap, gapPct));
        }

        rows.sort(Comparator.comparingDouble(PriceGapsResponse.Row::gapPct).reversed());
        return new PriceGapsResponse(rows.stream().limit(limit).toList());
    }
}
