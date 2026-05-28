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
 * 인증되지 않은 사용자의 요청을 처리하는 진입점 [401 Unauthorized, 인증 실패]
 *
 * <p>[흐름] 요청 -> JwuAuthenticationFilter (토큰 없음/유효하지 않음) -> SecurityContext 인증 정보 없음 -> Spring
 * Security가 AuthenticationException 발생 -> JwtAuthenticationEntryPoint.commence() 호출 -> 401 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

  // ErrorResponse 객체를 JSON 문자열로 직렬화하기 위한 Jackson ObjectMapper
  private final ObjectMapper objectMapper;

  /** 인증 실패 시 호출되는 메서드 */
  @Override
  public void commence(
      HttpServletRequest request, // 클라이언트 요청 객체
      HttpServletResponse response, // 클라이언트 응답 객체
      AuthenticationException authException // 인증 실패 원인이 되는 예외
      ) throws IOException, ServletException {
    log.error("Unauthorized error: {}", authException.getMessage());

    ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.UNAUTHORIZED);

    response.setContentType("application/json;charset=utf-8"); // 응답 타입: JSON, 한글 깨짐 방지
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // HTTP 상태코드 401 설정
    response.getWriter().write(objectMapper.writeValueAsString(errorResponse)); // 응답 Body에 JSON 작성
  }
}
