package com.pharmaprice.recommendation.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.repository.RegionRepository;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.HaversineDistanceCalculator;
import com.pharmaprice.recommendation.dto.BoundingBox;
import com.pharmaprice.recommendation.dto.Candidate;
import com.pharmaprice.recommendation.dto.DataSourceType;
import com.pharmaprice.recommendation.dto.LocationSource;
import com.pharmaprice.recommendation.dto.PharmacyPriceCandidate;
import com.pharmaprice.recommendation.dto.ScoredCandidate;
import com.pharmaprice.recommendation.dto.SearchResponse;
import com.pharmaprice.recommendation.dto.SortOption;
import com.pharmaprice.recommendation.repository.SearchQueryRepository;
import com.pharmaprice.report.domain.ReportSource;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 위치+약품 → Score 순 추천 결과(ROADMAP T-15, API.md §5). 거리 계산은 T-09 DistanceCalculator,
 * 순위 계산은 T-11 ScoreCalculator를 그대로 주입받아 쓴다 — 여기서 재구현하지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SearchService {

    private static final List<Integer> ALLOWED_RADII = List.of(500, 1000, 2000, 5000);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final DrugRepository drugRepository;
    private final RegionRepository regionRepository;
    private final SearchQueryRepository searchQueryRepository;
    private final PriceReportRepository priceReportRepository;
    private final DistanceCalculator distanceCalculator;
    private final ScoreCalculator scoreCalculator;

    public SearchResponse search(long drugId, Double lat, Double lng, String regionCode,
            int radius, SortOption sort, int limit) {
        if (!ALLOWED_RADII.contains(radius)) {
            throw new BusinessException(ErrorCode.INVALID_RADIUS);
        }
        Drug drug = drugRepository.findById(drugId)
                .filter(Drug::isOtcFlag)
                .orElseThrow(() -> new BusinessException(ErrorCode.DRUG_NOT_FOUND));
        ResolvedLocation location = resolveLocation(lat, lng, regionCode);
        int boundedLimit = Math.min(limit, 50);
        LocalDate today = LocalDate.now(KST);

        List<Candidate> candidates = new ArrayList<>();
        Map<Long, PharmacyPriceCandidate> byPharmacyId = new HashMap<>();
        collectCandidates(drugId, location, radius, candidates, byPharmacyId);

        SearchResponse.DrugSummary drugSummary = new SearchResponse.DrugSummary(
                drug.getId(), drug.getDisplayName(), drug.getPackageUnit(), drug.getImageUrl());
        SearchResponse.QueryEcho queryEcho = new SearchResponse.QueryEcho(
                location.lat(), location.lng(), radius, sort, location.source());

        if (candidates.isEmpty()) {
            SearchResponse.Summary emptySummary = new SearchResponse.Summary(0, null, null, null, null);
            SearchResponse.Suggestion suggestion = buildSuggestion(drugId, location, radius);
            return new SearchResponse(drugSummary, queryEcho, emptySummary, DataSourceType.SEED, List.of(), suggestion);
        }

        List<ScoredCandidate> scoreOrdered = scoreCalculator.rank(candidates, radius, today);
        long bestPharmacyId = scoreOrdered.get(0).candidate().pharmacyId();

        int candidateAvg = (int) Math.round(candidates.stream().mapToInt(Candidate::repPrice).average().orElseThrow());
        int candidateMin = candidates.stream().mapToInt(Candidate::repPrice).min().orElseThrow();
        int candidateMax = candidates.stream().mapToInt(Candidate::repPrice).max().orElseThrow();
        SearchResponse.Summary summary = new SearchResponse.Summary(
                candidates.size(), candidateAvg, candidateMin, candidateMax, candidateMax - candidateMin);

        List<ScoredCandidate> ordered = reorder(scoreOrdered, sort);
        List<ScoredCandidate> limited = ordered.size() > boundedLimit ? ordered.subList(0, boundedLimit) : ordered;

        List<Long> resultPharmacyIds = limited.stream().map(sc -> sc.candidate().pharmacyId()).toList();
        DataSourceType dataSource = classifyDataSource(drugId, resultPharmacyIds);

        List<SearchResponse.ResultItem> results = new ArrayList<>();
        int rank = 1;
        for (ScoredCandidate sc : limited) {
            PharmacyPriceCandidate row = byPharmacyId.get(sc.candidate().pharmacyId());
            SearchResponse.PharmacySummary pharmacySummary = new SearchResponse.PharmacySummary(
                    row.pharmacyId(), row.pharmacyName(), row.addressRoad(), row.lat(), row.lng(), row.phone());
            SearchResponse.PriceInfo priceInfo = new SearchResponse.PriceInfo(
                    row.repPrice(), row.minPrice(), row.avgPrice(), candidateAvg - row.repPrice(), row.reportCount(),
                    row.lastReportedAt(), ChronoUnit.DAYS.between(row.lastReportedAt(), today));
            results.add(new SearchResponse.ResultItem(
                    rank, sc.candidate().pharmacyId() == bestPharmacyId, pharmacySummary, priceInfo,
                    (int) Math.round(sc.candidate().distanceM()), sc.score(), sc.breakdown(), sc.badges()));
            rank++;
        }

        return new SearchResponse(drugSummary, queryEcho, summary, dataSource, results, null);
    }

    private void collectCandidates(long drugId, ResolvedLocation location, int radius,
            List<Candidate> candidates, Map<Long, PharmacyPriceCandidate> byPharmacyId) {
        BoundingBox box = distanceCalculator.boundingBox(location.lat(), location.lng(), radius);
        for (PharmacyPriceCandidate row : searchQueryRepository.findWithinBoundingBox(drugId, box)) {
            double distanceM = distanceCalculator.distanceMeters(location.lat(), location.lng(), row.lat(), row.lng());
            if (distanceM <= radius) {
                byPharmacyId.put(row.pharmacyId(), row);
                candidates.add(new Candidate(row.pharmacyId(), row.repPrice(), distanceM, row.lastReportedAt(), row.reportCount()));
            }
        }
    }

    private List<ScoredCandidate> reorder(List<ScoredCandidate> scoreOrdered, SortOption sort) {
        Comparator<ScoredCandidate> comparator = switch (sort) {
            case SCORE -> null;
            case PRICE -> Comparator.comparingInt((ScoredCandidate sc) -> sc.candidate().repPrice())
                    .thenComparingDouble(sc -> sc.candidate().distanceM())
                    .thenComparingLong(sc -> sc.candidate().pharmacyId());
            case DISTANCE -> Comparator.comparingDouble((ScoredCandidate sc) -> sc.candidate().distanceM())
                    .thenComparingInt(sc -> sc.candidate().repPrice())
                    .thenComparingLong(sc -> sc.candidate().pharmacyId());
        };
        return comparator == null ? scoreOrdered : scoreOrdered.stream().sorted(comparator).toList();
    }

    // 결과에 포함된 약국들의 유효 제보 출처를 보고 SEED/MIXED/USER를 판정한다. 통계 재계산의 90/180일 창과
    // 달리 배너 표시용 정보라 전체 유효 제보 기준으로 판정해도 충분하다.
    private DataSourceType classifyDataSource(long drugId, List<Long> pharmacyIds) {
        List<ReportSource> sources = priceReportRepository.findDistinctSources(drugId, pharmacyIds, ReportStatus.ACTIVE);
        boolean hasSeed = sources.contains(ReportSource.SEED);
        boolean hasUser = sources.stream().anyMatch(s -> s != ReportSource.SEED);
        if (hasSeed && hasUser) {
            return DataSourceType.MIXED;
        }
        return hasSeed ? DataSourceType.SEED : DataSourceType.USER;
    }

    private SearchResponse.Suggestion buildSuggestion(long drugId, ResolvedLocation location, int radius) {
        Integer nextRadius = ALLOWED_RADII.stream().filter(r -> r > radius).findFirst().orElse(null);
        if (nextRadius == null) {
            return null;
        }
        BoundingBox box = distanceCalculator.boundingBox(location.lat(), location.lng(), nextRadius);
        long estimatedCount = searchQueryRepository.findWithinBoundingBox(drugId, box).stream()
                .filter(row -> distanceCalculator.distanceMeters(location.lat(), location.lng(), row.lat(), row.lng()) <= nextRadius)
                .count();
        return new SearchResponse.Suggestion("EXPAND_RADIUS", nextRadius, estimatedCount);
    }

    private ResolvedLocation resolveLocation(Double lat, Double lng, String regionCode) {
        if (lat != null && lng != null) {
            if (!HaversineDistanceCalculator.isValidKoreanCoordinate(lat, lng)) {
                throw new BusinessException(ErrorCode.INVALID_COORDINATE);
            }
            return new ResolvedLocation(lat, lng, LocationSource.GPS);
        }
        if (regionCode != null && !regionCode.isBlank()) {
            Region region = regionRepository.findById(regionCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.VALIDATION_FAILED, "존재하지 않는 지역 코드입니다: " + regionCode));
            return new ResolvedLocation(region.getCenterLat(), region.getCenterLng(), LocationSource.REGION);
        }
        throw new BusinessException(ErrorCode.VALIDATION_FAILED, "lat/lng 또는 regionCode 중 하나는 필수입니다.");
    }

    private record ResolvedLocation(double lat, double lng, LocationSource source) {
    }
}
