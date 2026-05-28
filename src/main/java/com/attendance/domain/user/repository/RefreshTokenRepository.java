package com.attendance.domain.user.repository;

import com.attendance.domain.user.entity.RefreshToken;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

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

  /** userId로 해당 사용자의 RefreshToken 삭제 */
  void deleteByUserId(Long userId);

  /** 토큰 문자열로 RefreshToken 삭제 */
  void deleteByToken(String token);
}
