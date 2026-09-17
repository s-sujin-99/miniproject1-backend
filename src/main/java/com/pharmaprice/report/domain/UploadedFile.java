package com.pharmaprice.report.domain;

import java.time.OffsetDateTime;

import com.pharmaprice.auth.domain.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 업로드 파일(DATABASE.md §3.7). updated_at이 없어 created_at만 직접 둔다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "uploaded_file")
@EntityListeners(AuditingEntityListener.class)
public class UploadedFile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "original_name", nullable = false)
    private String originalName;

    @Column(name = "stored_path", nullable = false, length = 500)
    private String storedPath;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by")
    private AppUser uploadedBy;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Builder
    private UploadedFile(String originalName, String storedPath, String contentType, long sizeBytes,
            AppUser uploadedBy) {
        this.originalName = originalName;
        this.storedPath = storedPath;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.uploadedBy = uploadedBy;
    }
}
