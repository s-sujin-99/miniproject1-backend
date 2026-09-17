package com.pharmaprice.report.service;

/**
 * 파일 저장소 추상화(ROADMAP T-27). 지금은 로컬 디스크(LocalFileStorageService)뿐이지만,
 * 나중에 S3로 옮길 때 이 인터페이스 뒤로 구현체만 바꾸면 되게 해둔다.
 */
public interface FileStorageService {

    /** content를 저장하고, 이후 read()에 넘길 저장 키(경로)를 반환한다. */
    String store(byte[] content, String extension);

    /** storedPath(store()가 반환한 키)로 저장된 바이트를 읽어온다. */
    byte[] read(String storedPath);
}
