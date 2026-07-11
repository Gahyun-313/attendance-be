package com.attendance.global.config;

import com.attendance.global.security.StompHandshakeInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * STOMP 기반 WebSocket 설정
 *
 * <p>[왜 STOMP인가] 순수 WebSocket은 "메시지가 어떤 화면으로 가야 하는지"를 구분하는 개념이 없어 그걸 애플리케이션 코드가 직접 파싱해야 한다.
 * STOMP는 그 위에 pub/sub 프로토콜(destination 경로 기반 SUBSCRIBE/SEND)을 얹어서, 관리자 대시보드가
 * "/topic/attendance/{sessionId}"처럼 필요한 경로만 구독하고 서버는 그 경로로만 이벤트를 발행하는 구조를 쉽게 만들어준다.
 *
 * <p>[두 메서드의 역할 차이] registerStompEndpoints는 클라이언트가 "어느 URL로" 최초 연결(핸드셰이크)하는지 등록하고,
 * configureMessageBroker는 연결 이후 메시지가 "어느 경로 규칙으로" 오가는지 설정한다.
 */
@Configuration
@EnableWebSocketMessageBroker // STOMP 메시지 브로커 관련 빈들을 활성화
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandshakeInterceptor stompHandshakeInterceptor;

    // SecurityConfig의 CORS 설정과 동일한 값을 재사용 (관리자 웹 Origin)
    @Value("${cors.allowed-origins}")
    private String allowedOrigins;

    /**
     * 클라이언트가 WebSocket 연결을 맺는 엔드포인트 등록
     *
     * <p>addInterceptors로 StompHandshakeInterceptor를 붙여 핸드셰이크 시점에 JWT를 검증한다. 이 경로(/ws/**)는
     * SecurityConfig에서 permitAll 처리되어 있고, 인증은 이 인터셉터가 대신 수행한다.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry
                .addEndpoint("/ws")
                .setAllowedOrigins(allowedOrigins.split(","))
                .addInterceptors(stompHandshakeInterceptor);
    }

    /**
     * 메시지 라우팅 경로(prefix) 설정
     *
     * <p>enableSimpleBroker("/topic") : 서버 -> 클라이언트 방향. "/topic/..."으로 시작하는 목적지를 구독 중인
     * 클라이언트들에게 Spring 내장 인메모리 브로커가 그대로 전달한다 (Redis/RabbitMQ 같은 외부 브로커 없이도 단일 서버
     * 환경에서는 충분하다).
     *
     * <p>setApplicationDestinationPrefixes("/app") : 클라이언트 -> 서버 방향. "/app/..."으로 오는 메시지를
     * 컨트롤러의 MessageMapping 메서드가 처리한다. 현재는 관리자가 구독만 하고 서버로 보낼 메시지가 없어 당장 쓰이진 않지만,
     * 표준 STOMP 설정 관례상 미리 열어둔다.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic");
        registry.setApplicationDestinationPrefixes("/app");
    }
}