package com.attendance.domain.fcm.repository;

import com.attendance.domain.fcm.entity.FcmToken;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** FCM 토큰 Repository - 메서드 이름 규칙(findBy...)만으로 Spring Data JPA가 쿼리를 자동으로 만들어준다 */
public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

  /** 토큰 값으로 조회 - 등록 요청이 들어왔을 때 이미 등록된 토큰인지 확인하는 용도 */
  Optional<FcmToken> findByToken(String token);

  /** userId + token으로 조회 - 삭제할 때 "요청자 본인 토큰이 맞는지" 확인하는 용도 (남의 토큰을 못 지우게) */
  Optional<FcmToken> findByUserIdAndToken(Long userId, String token);

  /** 특정 사용자가 등록한 모든 토큰 조회 - 추후 알림 발송(Phase 4)에서 "이 사용자의 모든 기기로 보내기"에 사용 예정 */
  List<FcmToken> findAllByUserId(Long userId);
}
