package com.pharmaprice.report.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 로컬 디스크 구현. {app.upload-dir}/{yyyy}/{MM}/{uuid}.{ext}에 저장한다.
 * 경로는 전부 서버가 생성한 값(연·월·UUID·검증된 확장자)이라 원본 파일명이 경로에 들어갈 일이 없다(경로 조작 방지).
 */
@Component
public class LocalFileStorageService implements FileStorageService {

    private final Path root;

    public LocalFileStorageService(@Value("${app.upload-dir}") String uploadDir) {
        this.root = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @Override
    public String store(byte[] content, String extension) {
        LocalDate today = LocalDate.now();
        String relativePath = "%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(),
                UUID.randomUUID(), extension);
        Path target = root.resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, content);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return relativePath;
    }

    @Override
    public byte[] read(String storedPath) {
        try {
            return Files.readAllBytes(root.resolve(storedPath));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
