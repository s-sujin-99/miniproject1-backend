package com.pharmaprice.auth.domain;

import com.pharmaprice.common.domain.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자(DATABASE.md §3.2). user는 PG 예약어라 app_user.
 * GOOGLE 로그인 사용자는 비밀번호가 없어 passwordHash가 null일 수 있다(shrimp-rules.md §5.3).
 * {h-schema}는 프로파일별 default_schema로 치환돼 테스트가 운영 스키마를 건드리지 않는다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "app_user")
@SQLDelete(sql = "UPDATE {h-schema}app_user SET deleted_at = now() WHERE id = ?")
@SQLRestriction("deleted_at IS NULL")
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 30)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AuthProvider provider;

    // 비정규화 누적 제보 수, GET /auth/me 응답용
    @Column(name = "report_count", nullable = false)
    private int reportCount;

    @Builder
    private AppUser(String email, String passwordHash, String nickname, UserRole role, AuthProvider provider) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.role = role != null ? role : UserRole.USER;
        this.status = UserStatus.ACTIVE;
        this.provider = provider != null ? provider : AuthProvider.LOCAL;
    }

    /** 가격 제보 저장 성공 시 호출한다(GET /auth/me의 reportCount, DATABASE.md §3.2 비정규화 카운트). */
    public void increaseReportCount() {
        this.reportCount++;
    }

    /** 관리자가 제보를 REJECTED로 전환할 때 호출한다(ROADMAP T-32). 0 밑으로는 내려가지 않는다. */
    public void decreaseReportCount() {
        this.reportCount = Math.max(0, this.reportCount - 1);
    }
}
