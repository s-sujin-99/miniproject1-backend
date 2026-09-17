package com.pharmaprice.report.service;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.pharmaprice.auth.domain.UserRole;
import com.pharmaprice.auth.repository.AppUserRepository;
import com.pharmaprice.auth.security.AuthenticatedUser;
import com.pharmaprice.common.exception.BusinessException;
import com.pharmaprice.common.exception.ErrorCode;
import com.pharmaprice.report.domain.UploadedFile;
import com.pharmaprice.report.dto.DownloadedFile;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.repository.UploadedFileRepository;

import lombok.RequiredArgsConstructor;

/**
 * POST/GET /api/v1/uploads (ROADMAP T-27). 영수증 이미지 업로드 + 조회. OCR은 하지 않는다.
 * 파일 형식은 클라이언트가 보낸 Content-Type/확장자가 아니라 매직 바이트로 직접 판정한다.
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final long MAX_SIZE_BYTES = 5L * 1024 * 1024;
    private static final int MAX_ORIGINAL_NAME_LENGTH = 255;

    private static final byte[] JPEG_MAGIC = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
    private static final byte[] PNG_MAGIC =
            { (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A };

    private final FileStorageService fileStorageService;
    private final UploadedFileRepository uploadedFileRepository;
    private final AppUserRepository appUserRepository;

    @Transactional
    public UploadResponse upload(Long userId, MultipartFile file) {
        if (file.isEmpty()) {
            throw new BusinessException(ErrorCode.VALIDATION_FAILED, "파일이 비어 있습니다.");
        }
        if (file.getSize() > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] content = readBytes(file);
        if (content.length > MAX_SIZE_BYTES) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }
        String contentType = sniffImageContentType(content);
        if (contentType == null) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }

        String storedPath = fileStorageService.store(content, extensionFor(contentType));
        UploadedFile uploadedFile = UploadedFile.builder()
                .originalName(truncate(file.getOriginalFilename()))
                .storedPath(storedPath)
                .contentType(contentType)
                .sizeBytes(content.length)
                .uploadedBy(appUserRepository.getReferenceById(userId))
                .build();
        uploadedFileRepository.save(uploadedFile);

        return UploadResponse.from(uploadedFile);
    }

    @Transactional(readOnly = true)
    public DownloadedFile download(AuthenticatedUser principal, Long fileId) {
        UploadedFile uploadedFile = uploadedFileRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        boolean isOwner = uploadedFile.getUploadedBy() != null
                && uploadedFile.getUploadedBy().getId().equals(principal.userId());
        if (!isOwner && principal.role() != UserRole.ADMIN) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        byte[] content = fileStorageService.read(uploadedFile.getStoredPath());
        return new DownloadedFile(content, uploadedFile.getContentType(), uploadedFile.getOriginalName());
    }

    private static byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** 확장자나 클라이언트가 보낸 Content-Type이 아니라 실제 파일 내용(매직 바이트)으로 판정한다. */
    private static String sniffImageContentType(byte[] content) {
        if (startsWith(content, JPEG_MAGIC)) {
            return "image/jpeg";
        }
        if (startsWith(content, PNG_MAGIC)) {
            return "image/png";
        }
        if (isWebp(content)) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] content, byte[] magic) {
        if (content.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (content[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }

    // WEBP = "RIFF" + 4바이트 파일 크기 + "WEBP" (RIFF 컨테이너라 앞 4바이트만으로는 판별 불가).
    private static boolean isWebp(byte[] content) {
        return content.length >= 12
                && content[0] == 'R' && content[1] == 'I' && content[2] == 'F' && content[3] == 'F'
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P';
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/webp" -> "webp";
            default -> throw new IllegalStateException("검증되지 않은 content type: " + contentType);
        };
    }

    private static String truncate(String originalName) {
        String name = originalName == null || originalName.isBlank() ? "receipt" : originalName;
        return name.length() > MAX_ORIGINAL_NAME_LENGTH ? name.substring(0, MAX_ORIGINAL_NAME_LENGTH) : name;
    }
}
