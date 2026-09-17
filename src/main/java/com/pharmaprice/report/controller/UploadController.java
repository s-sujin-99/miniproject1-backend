package com.pharmaprice.report.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.pharmaprice.auth.security.AuthenticatedUser;
import com.pharmaprice.report.dto.DownloadedFile;
import com.pharmaprice.report.dto.UploadResponse;
import com.pharmaprice.report.service.UploadService;

import lombok.RequiredArgsConstructor;

/** API.md §6 영수증 업로드(ROADMAP T-27). OCR은 하지 않는다 — 관리자가 제보 검증 시 참고하는 용도. */
@RestController
@RequestMapping("/api/v1/uploads")
@RequiredArgsConstructor
public class UploadController {

    private final UploadService uploadService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public UploadResponse upload(@AuthenticationPrincipal AuthenticatedUser user,
            @RequestParam("file") MultipartFile file,
            // purpose는 API 계약상의 필드지만 현재 유일값(RECEIPT)이라 저장하지 않는다(uploaded_file에 컬럼 없음).
            @RequestParam("purpose") String purpose) {
        return uploadService.upload(user.userId(), file);
    }

    @GetMapping("/{fileId}")
    public ResponseEntity<byte[]> download(@AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable Long fileId) {
        DownloadedFile file = uploadService.download(user, fileId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.originalName() + "\"")
                .body(file.content());
    }
}
