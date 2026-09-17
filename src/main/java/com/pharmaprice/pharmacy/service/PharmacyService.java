package com.pharmaprice.pharmacy.service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.domain.Region;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse;
import com.pharmaprice.pharmacy.dto.PharmacyDetailResponse.DrugPriceResponse;
import com.pharmaprice.pharmacy.dto.PharmacyRegionResponse;
import com.pharmaprice.pharmacy.dto.PharmacySummaryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse;
import com.pharmaprice.pharmacy.dto.PriceHistoryResponse.PricePoint;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository;
import com.pharmaprice.pharmacy.repository.PharmacyQueryRepository.PharmacyRow;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.distance.DistanceCalculator;
import com.pharmaprice.recommendation.distance.HaversineDistanceCalculator;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.repository.PriceReportRepository;

import lombok.RequiredArgsConstructor;

/**
 * 약국 검색/상세/가격이력(ROADMAP T-19, T-20, API.md §4). 거리 계산은 T-09 DistanceCalculator를 재사용한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PharmacyService {

    private static final int MAX_RADIUS_M = 10000;
    private static final int MAX_HISTORY_DAYS = 365;
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final PharmacyRepository pharmacyRepository;
    private final PharmacyQueryRepository pharmacyQueryRepository;
    private final PriceReportRepository priceReportRepository;
    private final DistanceCalculator distanceCalculator;

    public PageResponse<PharmacySummaryResponse> search(String q, Double lat, Double lng, int radius, Pageable pageable) {
        boolean hasQuery = q != null && !q.isBlank();
        boolean hasLocation = lat != null && lng != null;
        if (!hasQuery && !hasLocation) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "q 또는 lat/lng 중 하나는 필수입니다.");
        }
        if (hasLocation && !HaversineDistanceCalculator.isValidKoreanCoordinate(lat, lng)) {
            throw new BusinessException(ErrorCode.INVALID_COORDINATE);
        }

        List<PharmacyRow> candidates = pharmacyQueryRepository.findCandidates(hasQuery ? q : null);
        List<PharmacySummaryResponse> all = hasLocation
                ? filterAndSortByDistance(candidates, lat, lng, Math.min(radius, MAX_RADIUS_M))
                : candidates.stream().map(row -> toSummary(row, null)).toList();

        int page = pageable.getPageNumber();
        int size = pageable.getPageSize();
        int from = Math.min(page * size, all.size());
        int to = Math.min(from + size, all.size());
        return PageResponse.of(all.subList(from, to), page, size, all.size());
    }

    public PharmacyDetailResponse getDetail(long pharmacyId, Double lat, Double lng) {
        Pharmacy pharmacy = pharmacyRepository.findById(pharmacyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHARMACY_NOT_FOUND));
        boolean hasLocation = lat != null && lng != null;
        if (hasLocation && !HaversineDistanceCalculator.isValidKoreanCoordinate(lat, lng)) {
            throw new BusinessException(ErrorCode.INVALID_COORDINATE);
        }
        Integer distanceM = hasLocation
                ? (int) Math.round(distanceCalculator.distanceMeters(lat, lng, pharmacy.getLat(), pharmacy.getLng()))
                : null;
        Region region = pharmacy.getRegion();
        PharmacyRegionResponse regionResponse = region == null ? null
                : new PharmacyRegionResponse(region.getCode(), region.getSido(), region.getSigungu());
        List<DrugPriceResponse> drugPrices = pharmacyQueryRepository.findDrugPrices(pharmacyId);

        return new PharmacyDetailResponse(pharmacy.getId(), pharmacy.getName(), pharmacy.getAddressRoad(),
                pharmacy.getAddressJibun(), pharmacy.getLat(), pharmacy.getLng(), pharmacy.getPhone(),
                pharmacy.getBusinessHours(), distanceM, regionResponse, drugPrices);
    }

    /** 이력 0건(약국·약품 조합이 존재하지 않거나 제보가 없음)이어도 빈 배열 + 200을 반환한다(404 아님, API.md §4). */
    public PriceHistoryResponse getPriceHistory(long pharmacyId, long drugId, int days) {
        int boundedDays = Math.min(days, MAX_HISTORY_DAYS);
        LocalDate fromDate = LocalDate.now(KST).minusDays(boundedDays);
        List<PricePoint> points = priceReportRepository
                .findHistory(pharmacyId, drugId, ReportStatus.ACTIVE, fromDate).stream()
                .map(row -> new PricePoint((LocalDate) row[0], ((Number) row[1]).intValue(), (Boolean) row[2]))
                .toList();
        return new PriceHistoryResponse(pharmacyId, drugId, points);
    }

    private List<PharmacySummaryResponse> filterAndSortByDistance(List<PharmacyRow> candidates, double lat, double lng, int radius) {
        return candidates.stream()
                .map(row -> toSummary(row, (int) Math.round(distanceCalculator.distanceMeters(lat, lng, row.lat(), row.lng()))))
                .filter(item -> item.distanceM() <= radius)
                .sorted(Comparator.comparingInt(PharmacySummaryResponse::distanceM))
                .toList();
    }

    private PharmacySummaryResponse toSummary(PharmacyRow row, Integer distanceM) {
        PharmacyRegionResponse region = row.regionCode() == null ? null
                : new PharmacyRegionResponse(row.regionCode(), row.regionSido(), row.regionSigungu());
        return new PharmacySummaryResponse(row.id(), row.name(), row.addressRoad(), row.lat(), row.lng(),
                row.phone(), distanceM, region);
    }
}
