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

/**
 * FCM 토큰 비즈니스 로직
 * - 등록/삭제만 있는 단순한 서비스라 복잡한 도메인 규칙은 없다.
 * - 핵심은 registerToken()의 "같은 토큰이 중복 저장되지 않게 하는" 처리 방식이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true) // 클래스 레벨 기본값: 조회 전용. 쓰기 메서드에만 아래처럼 @Transactional을 따로 붙여 덮어쓴다
public class FcmTokenService {

  private final FcmTokenRepository fcmTokenRepository;

  /**
   * FCM 토큰 등록
   * - token 컬럼에 unique 제약이 걸려 있어서, 이미 있는 토큰 값을 그대로 save()하면 예외가 발생한다.
   * - 그래서 먼저 findByToken으로 존재 여부를 확인해서
   *   있으면 소유자(userId)만 바꿔치기, 없으면 새로 생성하는 "upsert(있으면 수정, 없으면 생성)" 방식으로 처리한다.
   *   이렇게 하면 앱이 재실행될 때마다 같은 토큰으로 계속 요청을 보내도 에러 없이 안전하게(=멱등하게) 동작한다.
   */
  @Transactional
  public FcmTokenResponse registerToken(Long userId, FcmTokenRequest request) {
    FcmToken fcmToken =
        fcmTokenRepository
            .findByToken(request.getToken())
            .map(
                existing -> {
                  existing.reassignTo(userId);
                  return existing; // 영속 상태 엔티티를 수정만 하면, 트랜잭션 커밋 시 JPA가 변경 감지(dirty checking)로 알아서 UPDATE 쿼리를 날린다
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

  /**
   * FCM 토큰 삭제
   * - userId + token이 둘 다 일치하는 토큰만 찾아서 지운다.
   *   token 값만으로 찾아서 지우면, token 값을 알아낸 다른 사람이 남의 토큰 삭제를 요청할 수도 있기 때문에
   *   반드시 "요청자 본인 소유"라는 조건을 함께 검사한다.
   */
  @Transactional
  public void deleteToken(Long userId, String token) {
    FcmToken fcmToken =
        fcmTokenRepository
            .findByUserIdAndToken(userId, token)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.FCM_TOKEN_NOT_FOUND));
    fcmTokenRepository.delete(fcmToken);
  }
}
