package com.attendance.domain.fcm.service;

import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.SendResponse;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** FCM 발송 래퍼 정적 메서드 직접 호출 대신 컴포넌트로 감싸 테스트에서 대체(mock) 가능하게 한다. */
@Slf4j
@Component
public class FcmSender {

  /** FCM 멀티캐스트 발송 성공 건수와 무효 토큰(UNREGISTERED/INVALID_ARGUMENT) 목록을 반환 */
  public FcmSendResult send(List<String> tokens, String title, String body) {
    com.google.firebase.messaging.Notification notification =
        com.google.firebase.messaging.Notification.builder().setTitle(title).setBody(body).build();
    MulticastMessage message =
        MulticastMessage.builder().setNotification(notification).addAllTokens(tokens).build();

    try {
      BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
      List<SendResponse> responses = response.getResponses();
      List<String> invalidTokens = new ArrayList<>();

      // 무효 토큰만 별도 수집 (앱 재설치/로그아웃 등으로 만료된 토큰)
      for (int i = 0; i < responses.size(); i++) {
        SendResponse sendResponse = responses.get(i);
        if (sendResponse.isSuccessful()) {
          continue;
        }
        MessagingErrorCode errorCode = sendResponse.getException().getMessagingErrorCode();
        if (errorCode == MessagingErrorCode.UNREGISTERED
            || errorCode == MessagingErrorCode.INVALID_ARGUMENT) {
          invalidTokens.add(tokens.get(i));
        }
      }
      return new FcmSendResult(response.getSuccessCount(), invalidTokens);
    } catch (FirebaseMessagingException e) {
      // 네트워크 오류 등 전체 발송 실패
      log.error("FCM 발송 실패: {}", e.getMessage());
      return new FcmSendResult(0, List.of());
    }
  }

  public record FcmSendResult(int successCount, List<String> invalidTokens) {}
}
