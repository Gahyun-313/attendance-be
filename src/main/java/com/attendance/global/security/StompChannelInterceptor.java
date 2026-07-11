package com.attendance.global.security;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

/**
 * STOMP 메시지(SUBSCRIBE 등)에 대한 인가(권한) 처리.
 *
 * <p>StompHandshakeInterceptor는 "연결 자체"를 인증한다(로그인 여부만 확인). 반면 이 인터셉터는 "무엇을 구독할 수 있는지"를
 * 인가한다 - 예를 들어 로그인만 하면 누구나 WebSocket 연결은 되지만, 관리자 대시보드용 채널(/topic/attendance/**)은
 * ADMIN만 구독을 허용한다.
 *
 * <p>role 정보는 새로 조회하지 않고, StompHandshakeInterceptor가 핸드셰이크 시점에 WebSocket 세션 attributes에 저장해둔
 * 값을 그대로 이어받아 사용한다 (같은 세션이므로 attributes가 공유된다).
 */
@Slf4j
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String ATTENDANCE_TOPIC_PREFIX = "/topic/attendance";

    /**
     * 메시지가 브로커로 전달되기 직전에 호출됨 - 여기서 예외를 던지면 STOMP ERROR 프레임으로 클라이언트에 전달되고 연결이 끊긴다
     * (return null로 조용히 무시하는 방식과 달리, 클라이언트가 거부 사유를 확인할 수 있다).
     */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            String role = extractRole(accessor);

            if (destination != null
                    && destination.startsWith(ATTENDANCE_TOPIC_PREFIX)
                    && !ADMIN_ROLE.equals(role)) {
                log.warn("STOMP 구독 거부 - destination: {}, role: {}", destination, role);
                throw new MessagingException("ADMIN 권한이 필요한 구독입니다: " + destination);
            }
        }
        return message;
    }

    private String extractRole(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        return sessionAttributes != null ? (String) sessionAttributes.get("role") : null;
    }
}