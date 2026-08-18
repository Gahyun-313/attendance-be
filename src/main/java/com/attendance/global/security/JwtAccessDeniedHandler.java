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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 인가 실패(403) 처리 핸들러.
 * JWT는 유효하지만 권한이 부족할 때(@PreAuthorize 등) Spring Security가 던지는
 * AccessDeniedException을 잡아 JSON 에러 응답으로 변환.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

  private final ObjectMapper objectMapper;

  @Override
  public void handle(
      HttpServletRequest request,
      HttpServletResponse response,
      AccessDeniedException accessDeniedException)
      throws IOException, ServletException {
    log.error("Access denied error: {}", accessDeniedException.getMessage());

    ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.ACCESS_DENIED);

    response.setContentType("application/json;charset=UTF-8");
    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
  }
}
