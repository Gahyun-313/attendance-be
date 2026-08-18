package com.attendance.global.security;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * STOMP WebSocket 핸드셰이크(최초 연결) 시점에 JWT를 인증하는 인터셉터다. 브라우저 네이티브 WebSocket은 핸드셰이크 요청에 커스텀 헤더를 실을 수
 * 없어(new WebSocket(url)에는 헤더 옵션이 없다) 토큰을 쿼리 파라미터(?token=)로 받아 여기서 직접 검증한다. SecurityConfig가 /ws/**를
 * permitAll로 두고 인증 책임을 이곳으로 옮긴 이유도 그래서다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandshakeInterceptor implements HandshakeInterceptor {

  private final JwtTokenProvider jwtTokenProvider;

  /**
   * 연결 수립 전 호출, false 반환 시 핸드셰이크 자체가 거부된다. 검증에 성공하면 userId/role을 WebSocket 세션 attributes에 저장해, 이후
   * 구독 권한 제한 등에서 재사용한다.
   */
  @Override
  public boolean beforeHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Map<String, Object> attributes) {

    // 쿼리 파라미터에서 토큰 추출 및 유효성 검증
    String token = extractToken(request);

    if (token == null || !jwtTokenProvider.validateToken(token)) {
      log.warn("WebSocket handshake 거부: 토큰이 없거나 유효하지 않음");
      response.setStatusCode(HttpStatus.UNAUTHORIZED);
      return false;
    }

    // 검증된 사용자 정보를 세션 attributes에 저장, 이후 재사용
    attributes.put("userId", jwtTokenProvider.getUserId(token));
    attributes.put("role", jwtTokenProvider.getRole(token));
    return true;
  }

  /** 핸드셰이크 후처리. 실패 로깅은 beforeHandshake에서 이미 수행하므로 현재는 별도 처리가 없다. */
  @Override
  public void afterHandshake(
      ServerHttpRequest request,
      ServerHttpResponse response,
      WebSocketHandler wsHandler,
      Exception exception) {}

  /** 쿼리 파라미터(?token=xxx)에서 JWT 추출 */
  private String extractToken(ServerHttpRequest request) {
    if (request instanceof ServletServerHttpRequest servletRequest) {
      return servletRequest.getServletRequest().getParameter("token");
    }
    return null;
  }
}
