package com.attendance.domain.user.repository;

import com.attendance.domain.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** RefreshToken 엔티티의 저장/조회/삭제 처리로 JWT 갱신과 로그아웃 지원 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /** userId로 RefreshToken 조회 */
  Optional<RefreshToken> findByUserId(Long userId);

  /** 토큰 문자열로 RefreshToken 조회 */
  Optional<RefreshToken> findByToken(String token);

  /** userId로 RefreshToken 존재 여부 확인 */
  boolean existsByUserId(Long userId);

  /**
   * userId로 RefreshToken 삭제.
   * {@code @Modifying} 없는 평범한 delete는 실제 DELETE가 커밋 시점에야 나간다. 그런데
   * AuthService.login()에서 바로 뒤에 부르는 save()는 IDENTITY 전략이라 즉시 INSERT가 나가버려서
   * UNIQUE(user_id) 제약과 충돌할 수 있다. {@code @Modifying}+JPQL로 DELETE를 동기 실행시켜 막는다.
   */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  /** 토큰 문자열로 RefreshToken 삭제. deleteByUserId와 같은 이유로 @Modifying을 적용한다. */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
  void deleteByToken(@Param("token") String token);
}
