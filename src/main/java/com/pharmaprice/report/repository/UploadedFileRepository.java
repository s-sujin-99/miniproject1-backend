package com.pharmaprice.report.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pharmaprice.report.domain.UploadedFile;

public interface UploadedFileRepository extends JpaRepository<UploadedFile, Long> {
}
