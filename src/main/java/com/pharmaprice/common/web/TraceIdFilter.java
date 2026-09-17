package com.pharmaprice.common.web;

import java.io.IOException;
import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청마다 traceId를 발급해 MDC에 심는다(ROADMAP T-35). Security 필터 체인(order -100)보다도
 * 먼저 실행돼야 RestAuthenticationEntryPoint/RestAccessDeniedHandler가 만드는 에러 응답에도
 * 같은 traceId가 들어간다 — HIGHEST_PRECEDENCE로 순서를 강제한다.
 * ErrorResponse.of()가 이 값을 읽어 응답 traceId로 쓰고, logging.pattern.level(application.properties)이
 * 같은 값을 로그 줄마다 찍어 traceId 하나로 응답↔로그를 상호 검색할 수 있게 한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_KEY = "traceId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        MDC.put(TRACE_ID_KEY, UUID.randomUUID().toString().substring(0, 8));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TRACE_ID_KEY);
        }
    }
}
