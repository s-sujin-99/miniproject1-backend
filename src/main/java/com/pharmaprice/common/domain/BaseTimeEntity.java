package com.pharmaprice.common.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.MappedSuperclass;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.Getter;

/**
 * 모든 엔티티의 공통 감사 컬럼(created_at/updated_at). DATABASE.md 전 테이블 공통 정의.
 * 값은 AuditingEntityListener가 채우므로 setter를 두지 않는다(shrimp-rules.md §3.2).
 */
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
