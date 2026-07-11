package com.attendance.domain.user.repository;

import com.attendance.domain.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * RefreshTokenRepository: RefreshToken 엔티티에 대한 데이터 접근 계층
 *
 * <p>- JWT 기반 인증에서 Refresh Token의 저장, 조회, 삭제를 담당 - Access Token 갱신, 로그아웃, 토큰 검증 등의 작업에 사용
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

  /** userId로 RefreshToken 조회 */
  Optional<RefreshToken> findByUserId(Long userId);

  /** 토큰 문자열로 RefreshToken 조회 */
  Optional<RefreshToken> findByToken(String token);

  /** userId로 RefreshToken 존재 여부 확인 */
  boolean existsByUserId(Long userId);

  /**
   * userId로 해당 사용자의 RefreshToken 삭제
   *
   * <p>{@code @Modifying} 없는 평범한 derived delete 메서드는 "먼저 조회해서 각 엔티티를 remove() 예약"만 해두고 실제
   * DELETE는 영속성 컨텍스트가 flush될 때(보통 트랜잭션 커밋 시점)에야 나간다. 그런데 AuthService.login()에서 이 메서드
   * 바로 뒤에 호출하는 save()는 새 RefreshToken이 IDENTITY 전략이라 save() 즉시 INSERT가 나가버린다 - 즉 "삭제 예약"이
   * 아직 반영 안 된 상태에서 같은 user_id로 새 행을 넣으려다 UNIQUE(user_id) 제약에 걸려 실패하는 버그가 있었다
   * (재로그인 시 500 에러로 재현됨). {@code @Modifying} + JPQL로 바꿔 DELETE를 즉시(동기) 실행되게 해서 해결.
   */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.userId = :userId")
  void deleteByUserId(@Param("userId") Long userId);

  /** 토큰 문자열로 RefreshToken 삭제 - 위와 같은 이유로 @Modifying 적용 */
  @Modifying(clearAutomatically = true)
  @Query("DELETE FROM RefreshToken r WHERE r.token = :token")
  void deleteByToken(@Param("token") String token);
}