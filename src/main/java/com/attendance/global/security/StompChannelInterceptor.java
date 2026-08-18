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
 * STOMP 메시지(SUBSCRIBE 등) 인가.
 * StompHandshakeInterceptor가 "연결"을 인증한다면, 이 인터셉터는 "무엇을 구독할 수 있는지"를 인가한다
 * (예: /topic/attendance/**는 ADMIN만 구독 허용). role은 새로 조회하지 않고, 핸드셰이크 시점에
 * 세션 attributes에 저장해둔 값을 그대로 사용한다.
 */
@Slf4j
@Component
public class StompChannelInterceptor implements ChannelInterceptor {

  private static final String ADMIN_ROLE = "ADMIN";
  private static final String ATTENDANCE_TOPIC_PREFIX = "/topic/attendance";

  /** 메시지가 브로커로 전달되기 직전 호출. 예외를 던지면 STOMP ERROR 프레임으로 클라이언트에 거부 사유가 전달되고 연결이 끊긴다. */
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
