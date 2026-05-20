package com.attendance.global.security;

import com.attendance.global.exception.ErrorCode;
import com.attendance.global.response.ErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 인가(Authorization) 실패 시 처리하는 핸들러 [403 Forbidden, 인가 실패]
 * - JWT 토큰은 유효하지만 접근 권한이 없음
 *
 * [흐름]
 * 요청 -> JwuAuthenticationFilter (토큰 유효)
 *      -> SecurityContext 인증 정보 세팅 완료
 *      -> 권한 체크 실패 (@PreAuthorize, SecurityConfig 경로 설정 등)
 *      -> Spring Security가 AccessDeniedException 발생
 *      -> JwtAccessDeniedHandler.handle() 호출
 *      -> 403 응답 반환
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAccessDeniedHandler implements AccessDeniedHandler {

    // ErrorResponse 객체를 JSON 문자열로 직렬화하기 위한 Jackson ObjectMapper
    private final ObjectMapper objectMapper;

    /**
     * 인가 실패 시 호출되는 메서드
     */
    @Override
    public void handle(
            HttpServletRequest request, // 클라이언트 요청 객체
            HttpServletResponse response,   // 클라이언트 응답 객체
            AccessDeniedException accessDeniedException // 인가 실패 원인이 되는 예외 (권한 부족)
    ) throws IOException, ServletException {
        log.error("Access denied error: {}", accessDeniedException.getMessage());

        ErrorResponse errorResponse = ErrorResponse.of(ErrorCode.ACCESS_DENIED);

        response.setContentType("application/json;charset=UTF-8");  // 응답 타입: JSON, 한글 깨짐 방지
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);   // HTTP 상태코드 403 설정
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse)); // 응답 Body에 JSON 작성
    }
}
