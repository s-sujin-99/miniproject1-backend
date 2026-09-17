package com.pharmaprice.report.service;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.common.dto.PageResponse;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.drug.repository.DrugRepository;
import com.pharmaprice.pharmacy.domain.Pharmacy;
import com.pharmaprice.pharmacy.repository.PharmacyRepository;
import com.pharmaprice.recommendation.dto.PriceStat;
import com.pharmaprice.recommendation.service.PriceStatService;
import com.pharmaprice.report.domain.FlagReason;
import com.pharmaprice.report.domain.PriceReport;
import com.pharmaprice.report.domain.ReportStatus;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.PriceReportListItemResponse;
import com.pharmaprice.report.dto.PriceReportRequest;
import com.pharmaprice.report.dto.PriceReportResponse;
import com.pharmaprice.report.repository.PriceReportRepository;
import com.pharmaprice.report.repository.UploadedFileRepository;

import lombok.RequiredArgsConstructor;

/**
 * POST /api/v1/price-reports (ROADMAP T-26). 저장 + 통계 재계산 + 제보자 report_count 증가를
 * 하나의 트랜잭션에서 처리한다. 통계 재계산은 T-10 PriceStatService.recalculate()를 그대로 재사용한다.
 */
@Service
@RequiredArgsConstructor
public class PriceReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final int MAX_PAST_DAYS = 180;

    // F2-9 — 약품 전체 중앙값의 0.3배 미만/3배 초과면 이상치로 본다.
    private static final double OUTLIER_LOW_RATIO = 0.3;
    private static final double OUTLIER_HIGH_RATIO = 3.0;

    private final PriceReportRepository priceReportRepository;
    private final PharmacyRepository pharmacyRepository;
    private final DrugRepository drugRepository;
    private final AppUserRepository appUserRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final PriceStatService priceStatService;

    @Transactional
    public PriceReportResponse createReport(Long userId, PriceReportRequest request) {
        Pharmacy pharmacy = pharmacyRepository.findById(request.pharmacyId())
                .filter(Pharmacy::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.PHARMACY_NOT_FOUND));
        Drug drug = drugRepository.findById(request.drugId())
                .orElseThrow(() -> new BusinessException(ErrorCode.DRUG_NOT_FOUND));
        if (!drug.isOtcFlag()) {
            throw new BusinessException(ErrorCode.DRUG_NOT_OTC);
        }

        LocalDate today = LocalDate.now(KST);
        LocalDate purchasedAt = request.purchasedAt() != null ? request.purchasedAt() : today;
        validateDateRange(purchasedAt, today);

        AppUser user = appUserRepository.getReferenceById(userId);
        UploadedFile receiptFile = request.receiptFileId() == null
                ? null
                // 존재하지 않는 파일 id는 조용히 무시한다 — T-27(업로드 API) 이전에는 애초에 발급된 id가 없다.
                : uploadedFileRepository.findById(request.receiptFileId()).orElse(null);

        PriceReport report = PriceReport.builder()
                .pharmacy(pharmacy).drug(drug).user(user)
                .price(request.price()).purchasedAt(purchasedAt)
                .receiptFile(receiptFile).memo(request.memo())
                .build();

        OutlierCheck outlierCheck = detectOutlier(drug.getId(), request.price());
        if (outlierCheck.reason() != null) {
            report.flagAsOutlier(outlierCheck.reason());
        }

        try {
            // uq_report_user_pair_day 위반은 커밋 시점이 아니라 지금 바로 확인해야 여기서 잡을 수 있다.
            priceReportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.DUPLICATE_REPORT);
        }

        user.increaseReportCount();

        PriceStat updatedStat = priceStatService.recalculate(pharmacy.getId(), drug.getId()).orElse(null);
        String warning = outlierCheck.reason() == null ? null : buildWarning(outlierCheck.median());

        return PriceReportResponse.of(report, warning, updatedStat);
    }

    /** GET /api/v1/price-reports (ROADMAP T-28). mine=true인데 비로그인이면 401. */
    @Transactional(readOnly = true)
    public PageResponse<PriceReportListItemResponse> list(Long pharmacyId, Long drugId, boolean mine, Long userId,
            Pageable pageable) {
        if (mine && userId == null) {
            throw new BusinessException(ErrorCode.UNAUTHENTICATED);
        }
        // mine=true(/me)는 본인 HIDDEN 제보도 봐야 하니 상태 필터를 걸지 않는다(ROADMAP T-30).
        Page<PriceReport> page = priceReportRepository.search(pharmacyId, drugId, mine ? userId : null,
                mine ? null : ReportStatus.ACTIVE, null, pageable);
        return PageResponse.of(page.getContent().stream().map(PriceReportListItemResponse::from).toList(),
                pageable.getPageNumber(), pageable.getPageSize(), page.getTotalElements());
    }

    private void validateDateRange(LocalDate purchasedAt, LocalDate today) {
        if (purchasedAt.isAfter(today)) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "구매일은 미래일 수 없습니다.");
        }
        if (purchasedAt.isBefore(today.minusDays(MAX_PAST_DAYS))) {
            throw new BusinessException(ErrorCode.INVALID_DATE_RANGE, "구매일은 180일 이전일 수 없습니다.");
        }
    }

    private record OutlierCheck(FlagReason reason, double median) {
    }

    /** 비교할 기존 제보가 없으면(이 약품의 첫 제보) 판정하지 않는다. */
    private OutlierCheck detectOutlier(long drugId, int price) {
        Double median = priceReportRepository.findMedianPriceByDrugId(drugId);
        if (median == null) {
            return new OutlierCheck(null, 0);
        }
        if (price < median * OUTLIER_LOW_RATIO) {
            return new OutlierCheck(FlagReason.OUTLIER_LOW, median);
        }
        if (price > median * OUTLIER_HIGH_RATIO) {
            return new OutlierCheck(FlagReason.OUTLIER_HIGH, median);
        }
        return new OutlierCheck(null, median);
    }

    private String buildWarning(double median) {
        long low = Math.round(median * OUTLIER_LOW_RATIO);
        long high = Math.round(median * OUTLIER_HIGH_RATIO);
        return "입력하신 가격이 이 약품의 일반적인 가격대(%,d~%,d원)와 크게 달라 통계에 반영되지 않았습니다. 관리자 확인 후 반영됩니다."
                .formatted(low, high);
    }
}
