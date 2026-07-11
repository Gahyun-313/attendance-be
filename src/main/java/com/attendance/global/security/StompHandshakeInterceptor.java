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
 * STOMP WebSocket 핸드셰이크(최초 연결) 시점 JWT 인증 인터셉터
 *
 * <p>REST API는 매 요청마다 Authorization 헤더로 JWT를 검증하지만(JwtAuthenticationFilter), 브라우저 네이티브
 * WebSocket은 핸드셰이크 요청에 커스텀 헤더를 실어 보낼 수 없다 (new WebSocket(url)에는 헤더 옵션이 없음). 그래서 토큰을
 * 쿼리 파라미터(?token=)로 받아 여기서 직접 검증한다. SecurityConfig에서 /ws/** 경로를 permitAll로 두고
 * JwtAuthenticationFilter 대상에서 제외한 것도 인증 책임을 이 인터셉터로 옮겼기 때문이다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    /**
     * 연결 수립 전 호출됨 - 여기서 false를 반환하면 핸드셰이크 자체가 거부되어 WebSocket 연결이 성립되지 않는다.
     *
     * <p>검증에 성공하면 userId/role을 WebSocket 세션 attributes에 저장해두고, Phase 2(구독 권한 제한, 이벤트 발행 시
     * 발신자 식별 등)에서 재사용할 수 있게 한다.
     */
    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes) {

        String token = extractToken(request);

        if (token == null || !jwtTokenProvider.validateToken(token)) {
            log.warn("WebSocket handshake 거부: 토큰이 없거나 유효하지 않음");
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return false;
        }

        attributes.put("userId", jwtTokenProvider.getUserId(token));
        attributes.put("role", jwtTokenProvider.getRole(token));
        return true;
    }

    /** 핸드셰이크(성공/실패) 이후 후처리 - 현재는 별도 후처리 없음. 실패 로깅은 beforeHandshake에서 이미 수행. */
    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception) {}

    /** 쿼리 파라미터(?token=xxx)에서 JWT 추출 - WebSocket 핸드셰이크는 일반 서블릿 요청 위에서 이루어지므로 캐스팅해서 꺼낸다. */
    private String extractToken(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            return servletRequest.getServletRequest().getParameter("token");
        }
        return null;
    }
}