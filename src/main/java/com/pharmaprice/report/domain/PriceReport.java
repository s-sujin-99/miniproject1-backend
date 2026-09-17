package com.pharmaprice.report.domain;

import java.time.LocalDate;

import com.pharmaprice.auth.domain.AppUser;
import com.pharmaprice.common.domain.BaseEntity;
import com.pharmaprice.drug.domain.Drug;
import com.pharmaprice.pharmacy.domain.Pharmacy;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 가격 제보(DATABASE.md §3.5). status(ACTIVE/HIDDEN)는 노출 상태이고 삭제는 deleted_at으로 한다(shrimp-rules.md §2).
 * 가격 CHECK와 중복 제보 부분 유니크 인덱스는 마이그레이션(V1__init.sql)에서 건다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "price_report")
@SQLDelete(sql = "UPDATE {h-schema}price_report SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class PriceReport extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pharmacy_id", nullable = false)
    private Pharmacy pharmacy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "drug_id", nullable = false)
    private Drug drug;

    // 시드 데이터는 제보자가 없다
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(nullable = false)
    private int price;

    @Column(name = "purchased_at", nullable = false)
    private LocalDate purchasedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportSource source;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReportStatus status;

    @Column(nullable = false)
    private boolean flagged;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_reason", length = 100)
    private FlagReason flagReason;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receipt_file_id")
    private UploadedFile receiptFile;

    @Column(length = 200)
    private String memo;

    @Builder
    private PriceReport(Pharmacy pharmacy, Drug drug, AppUser user, int price, LocalDate purchasedAt,
            ReportSource source, UploadedFile receiptFile, String memo) {
        this.pharmacy = pharmacy;
        this.drug = drug;
        this.user = user;
        this.price = price;
        this.purchasedAt = purchasedAt;
        this.source = source != null ? source : ReportSource.FORM;
        this.status = ReportStatus.ACTIVE;
        this.receiptFile = receiptFile;
        this.memo = memo;
    }

    /** F2-9 — 이상치로 판정된 제보를 표시한다. 저장은 그대로 하되 통계 재계산에서는 제외된다. */
    public void flagAsOutlier(FlagReason reason) {
        this.flagged = true;
        this.flagReason = reason;
    }

    /** 관리자 제보 관리 — 노출 상태 변경(ACTIVE/HIDDEN/REJECTED). ROADMAP T-32, API.md §8 PATCH. */
    public void changeStatus(ReportStatus status) {
        this.status = status;
    }

    /** 관리자 제보 관리 — 이상치 플래그 해제/설정. flagReason은 건드리지 않는다(reason 필드로 별도 관리). */
    public void changeFlagged(boolean flagged) {
        this.flagged = flagged;
    }

    /** 관리자가 자유 텍스트 사유로 개입했음을 표시한다. 사유 원문은 저장하지 않는다(스키마에 컬럼 없음). */
    public void markFlagReasonManual() {
        this.flagReason = FlagReason.MANUAL;
    }
}
