package com.pharmaprice.report.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import com.pharmaprice.AbstractIntegrationTest;
import com.pharmaprice.auth.dto.LoginResponse;

import tools.jackson.databind.ObjectMapper;

/**
 * ROADMAP T-27 완료 판정 — 영수증 업로드/조회를 실제 HTTP 경로로 검증한다.
 * "서버 재시작 후에도 파일 유지"는 JUnit으로 실제 재시작을 흉내낼 수 없어, 대신 같은 세션 안에서
 * 저장한 바이트를 GET으로 다시 읽어 디스크에서 원문 그대로 돌아오는지 확인하는 것으로 대체한다
 * (LocalFileStorageService.read()는 메모리 캐시가 아니라 항상 디스크에서 읽는다).
 */
class UploadControllerTest extends AbstractIntegrationTest {

    @Autowired
    ObjectMapper objectMapper;

    private static final byte[] JPEG_BYTES =
            concat(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF }, "fake-jpeg-body".getBytes(StandardCharsets.UTF_8));

    @Test
    void 정상_업로드는_201과_url을_반환한다() throws Exception {
        String token = signupAndLogin();

        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contentType").value("image/jpeg"))
                .andExpect(jsonPath("$.sizeBytes").value(JPEG_BYTES.length))
                .andExpect(jsonPath("$.url").value(org.hamcrest.Matchers.startsWith("/api/v1/uploads/")));
    }

    @Test
    void _5MB_초과_파일은_413_FILE_TOO_LARGE다() throws Exception {
        String token = signupAndLogin();
        byte[] tooBig = concat(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF },
                new byte[5 * 1024 * 1024 + 1]);

        mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", tooBig))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void 확장자만_jpg인_PDF는_415_UNSUPPORTED_FILE_TYPE다() throws Exception {
        String token = signupAndLogin();
        byte[] fakeJpg = "%PDF-1.4 진짜는 PDF".getBytes(StandardCharsets.UTF_8);

        mockMvc.perform(multipart("/api/v1/uploads")
                        // Content-Type도, 파일명 확장자도 jpg로 위장했지만 매직 바이트로 판정하므로 통하지 않는다.
                        .file(new MockMultipartFile("file", "fake.jpg", "image/jpeg", fakeJpg))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void 업로드한_본인은_파일을_다시_읽을_수_있고_내용이_그대로다() throws Exception {
        String token = signupAndLogin();
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isCreated())
                .andReturn();
        long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("id").asLong();

        MvcResult downloadResult = mockMvc.perform(get("/api/v1/uploads/" + fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentType(MediaType.IMAGE_JPEG))
                .andReturn();
        assertThat(downloadResult.getResponse().getContentAsByteArray()).isEqualTo(JPEG_BYTES);
    }

    @Test
    void 타인_파일을_조회하면_403_FORBIDDEN이다() throws Exception {
        String ownerToken = signupAndLogin();
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();
        long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("id").asLong();

        String otherToken = signupAndLogin();
        mockMvc.perform(get("/api/v1/uploads/" + fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + otherToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    // 시드 admin@example.com — 원문 비밀번호는 tools/seed-generator/build_master_seed.py 주석에 있다.
    @Test
    void ADMIN은_타인_파일도_조회할_수_있다() throws Exception {
        String ownerToken = signupAndLogin();
        MvcResult uploadResult = mockMvc.perform(multipart("/api/v1/uploads")
                        .file(new MockMultipartFile("file", "receipt.jpg", "image/jpeg", JPEG_BYTES))
                        .param("purpose", "RECEIPT")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + ownerToken))
                .andExpect(status().isCreated())
                .andReturn();
        long fileId = objectMapper.readTree(uploadResult.getResponse().getContentAsString()).path("id").asLong();

        String adminToken = login("admin@example.com", "Admin1234!");
        mockMvc.perform(get("/api/v1/uploads/" + fileId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    private String signupAndLogin() throws Exception {
        String email = "upload-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new SignupPayload(email, "Password12", "업로드테스터"))))
                .andExpect(status().isCreated());
        return login(email, "Password12");
    }

    private String login(String email, String password) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginPayload(email, password))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readValue(loginResult.getResponse().getContentAsString(), LoginResponse.class)
                .accessToken();
    }

    private record SignupPayload(String email, String password, String nickname) {
    }

    private record LoginPayload(String email, String password) {
    }
}
