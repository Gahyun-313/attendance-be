package com.attendance.domain.fcm.service;

import com.attendance.domain.fcm.dto.FcmTokenRequest;
import com.attendance.domain.fcm.dto.FcmTokenResponse;
import com.attendance.domain.fcm.entity.FcmToken;
import com.attendance.domain.fcm.repository.FcmTokenRepository;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** FCM 토큰 비즈니스 로직 처리. 등록/삭제만 있는 단순한 서비스이며, 핵심은 registerToken()의 중복 방지 처리다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 레벨 기본값은 조회 전용이다. 쓰기 메서드에는 아래처럼 @Transactional을 따로 붙여 덮어쓴다.
public class FcmTokenService {

  private final FcmTokenRepository fcmTokenRepository;

  /**
   * FCM 토큰 등록
   * token에 unique 제약이 있어 이미 존재하는 값을 그대로 save()하면 예외가 난다. findByToken으로
   * 있으면 소유자만 바꾸고, 없으면 새로 생성하는 upsert 방식으로 처리해 재요청에도 멱등하게 동작하게 한다.
   */
  @Transactional
  public FcmTokenResponse registerToken(Long userId, FcmTokenRequest request) {
    FcmToken fcmToken =
        fcmTokenRepository
            .findByToken(request.getToken())
            .map(
                existing -> {
                  existing.reassignTo(userId);
                  return existing; // 영속 상태라 커밋 시 dirty checking으로 UPDATE가 나간다.
                })
            .orElseGet(
                () ->
                    fcmTokenRepository.save(
                        FcmToken.builder()
                            .userId(userId)
                            .token(request.getToken())
                            .deviceType(request.getDeviceType())
                            .build()));
    return FcmTokenResponse.from(fcmToken);
  }

  /** FCM 토큰 삭제. token만으로 찾으면 남의 토큰도 지울 수 있어, userId+token이 둘 다 일치해야 삭제되도록 소유자를 검증한다. */
  @Transactional
  public void deleteToken(Long userId, String token) {
    FcmToken fcmToken =
        fcmTokenRepository
            .findByUserIdAndToken(userId, token)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.FCM_TOKEN_NOT_FOUND));
    fcmTokenRepository.delete(fcmToken);
  }
}
