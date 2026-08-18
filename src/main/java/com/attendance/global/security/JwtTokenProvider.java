package com.attendance.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** JWT 토큰 생성/검증. HMAC-SHA256 대칭키 서명 방식이라 단일 서버 환경에 적합하다. */
@Slf4j
@Component
public class JwtTokenProvider {

  private final SecretKey secretKey;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  /** application.yml의 JWT 설정으로 초기화. secret은 운영 환경에서는 환경변수/Vault로 관리한다. */
  public JwtTokenProvider(
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration}") long accessTokenExpiration,
      @Value("${jwt.refresh-token-expiration}") long refreshTokenExpiration) {
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  /** Access Token 생성 */
  public String createAccessToken(Long userId, String username, String role) {
    Date now = new Date();
    Date validity = new Date(now.getTime() + accessTokenExpiration);

    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .claim("role", role)
        .issuedAt(now)
        .expiration(validity)
        .signWith(secretKey)
        .compact();
  }

  /** Refresh Token 생성. 재발급 시 식별 용도로만 쓰여 role은 담지 않는다. */
  public String createRefreshToken(Long userId, String username) {
    Date now = new Date();
    Date validity = new Date(now.getTime() + refreshTokenExpiration);

    return Jwts.builder()
        .subject(username)
        .claim("userId", userId)
        .issuedAt(now)
        .expiration(validity)
        .signWith(secretKey)
        .compact();
  }

  /** 토큰에서 사용자 ID 추출 */
  public Long getUserId(String token) {
    Claims claims = parseClaims(token);
    return claims.get("userId", Long.class);
  }

  /** 토큰에서 사용자 이름 추출 */
  public String getUserName(String token) {
    return parseClaims(token).getSubject();
  }

  /** 토큰에서 역할 추출 */
  public String getRole(String token) {
    Claims claims = parseClaims(token);
    return claims.get("role", String.class);
  }

  /** AuthService에서 @Value 없이 만료 시간을 쓸 수 있도록 초 단위로 변환해 반환한다. */
  public long getAccessTokenExpirationSeconds() {
    return accessTokenExpiration / 1000;
  }

  /** RefreshToken 엔티티의 expiresAt 계산용으로 만료 시간을 초 단위로 반환한다. */
  public long getRefreshTokenExpirationSeconds() {
    return refreshTokenExpiration / 1000;
  }

  /** 토큰 유효성 검증. 서명/만료/구조 검증은 parseClaims()가 수행하며, 실패하면 예외 대신 로그만 남기고 false를 반환한다. */
  public boolean validateToken(String token) {
    try {
      parseClaims(token);
      return true;
    } catch (SecurityException | MalformedJwtException e) {
      log.error("Invalid JWT signature: {}", e.getMessage());
    } catch (ExpiredJwtException e) {
      log.error("Expired JWT token: {}", e.getMessage());
    } catch (UnsupportedJwtException e) {
      log.error("Unsupported JWT token: {}", e.getMessage());
    } catch (IllegalArgumentException e) {
      log.error("JWT claims string is empty: {}", e.getMessage());
    }
    return false;
  }

  /** 토큰 만료 여부만 단독 확인 */
  public boolean isTokenExpired(String token) {
    try {
      Claims claims = parseClaims(token);
      return claims.getExpiration().before(new Date());
    } catch (ExpiredJwtException e) {
      return true; // 파싱 자체가 실패할 정도면 만료된 것으로 처리한다.
    }
  }

  /** 토큰 파싱 및 서명 검증. 예외 처리는 호출부에서 담당한다. */
  private Claims parseClaims(String token) {
    return Jwts.parser().verifyWith(secretKey).build().parseSignedClaims(token).getPayload();
  }
}
