package com.attendance.global.security;

import com.attendance.global.exception.ErrorCode;
import com.attendance.global.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * 인증되지 않은 요청(401) 처리. 토큰이 없거나 유효하지 않아 Spring Security가 던지는 AuthenticationException을 JSON 에러 응답으로
 * 변환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ObjectMapper objectMapper;

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException, ServletException {
    log.error("Unauthorized error: {}", authException.getMessage());

    ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.UNAUTHORIZED);

    response.setContentType("application/json;charset=utf-8");
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
