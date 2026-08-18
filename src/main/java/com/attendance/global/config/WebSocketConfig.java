package com.attendance.global.config;

import com.attendance.global.security.StompChannelInterceptor;
import com.attendance.global.security.StompHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 기반 WebSocket 설정 순수 WebSocket과 달리 pub/sub 프로토콜(destination 기반 SUBSCRIBE/SEND)을 얹어서, 대시보드가
 * "/topic/attendance/{sessionId}" 같은 경로만 구독하는 구조를 쉽게 만들 수 있다.
 */
@Configuration
@EnableWebSocketMessageBroker // STOMP 메시지 브로커 관련 빈들을 활성화한다.
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

  private final StompHandshakeInterceptor stompHandshakeInterceptor;
  private final StompChannelInterceptor stompChannelInterceptor;

  // SecurityConfig의 CORS 설정과 동일한 값 재사용(관리자 웹 Origin)
  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * WebSocket 연결 엔드포인트 등록 /ws는 SecurityConfig에서 permitAll로 처리돼 있어서, StompHandshakeInterceptor가
   * 핸드셰이크 시점에 대신 JWT를 검증한다.
   */
  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    registry
        .addEndpoint("/ws")
        .setAllowedOrigins(allowedOrigins.split(","))
        .addInterceptors(stompHandshakeInterceptor);
  }

  /**
   * 메시지 라우팅 prefix 설정 "/topic"은 서버에서 클라이언트로 향하며, Spring 내장 인메모리 브로커가 구독자에게 전달한다(단일 서버라 충분). "/app"은
   * 클라이언트에서 서버로 향하며, 지금은 쓰지 않지만 표준 STOMP 관례상 미리 열어둔다.
   */
  @Override
  public void configureMessageBroker(MessageBrokerRegistry registry) {
    registry.enableSimpleBroker("/topic");
    registry.setApplicationDestinationPrefixes("/app");
  }

  /**
   * 클라이언트에서 서버로 오는 STOMP 메시지(SUBSCRIBE 등)에 인터셉터 등록. StompChannelInterceptor가 /topic/attendance/**
   * 구독을 ADMIN으로만 제한한다.
   */
  @Override
  public void configureClientInboundChannel(ChannelRegistration registration) {
    registration.interceptors(stompChannelInterceptor);
  }
}
