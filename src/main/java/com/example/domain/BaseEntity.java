package com.example.domain;

import java.time.OffsetDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;

import lombok.Getter;

/**
 * Soft Delete 대상 엔티티(shrimp-rules.md §4.3)의 공통 부모.
 * 구체 엔티티는 클래스에 아래 2개를 직접 선언해야 한다(SQL에 테이블명이 필요해 여기서 대신할 수 없음):
 *   @SQLDelete(sql = "UPDATE pharmaprice.{table} SET deleted_at = now() WHERE id = ?")
 *   @SQLRestriction("deleted_at IS NULL")
 */
@Getter
@MappedSuperclass
public abstract class BaseEntity extends BaseTimeEntity {

    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public void softDelete() {
        this.deletedAt = OffsetDateTime.now();
    }
}
