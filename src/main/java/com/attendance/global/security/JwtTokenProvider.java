package com.attendance.global.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import javax.crypto.SecretKey;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * JWT 토큰 생성 및 검증을 담당하는 Provider 클래스
 *
 * <p>- AccessToken, Refresh Token 생성 - 토큰에서 사용자 정보 추출 (userId, username, role) - 토큰 유효성 검증 (서명,
 * 만료시간) - HMAC-SHA256 알고리즘을 사용한 서명 (대칭키, 단일 서버 환경에 적합)
 */
@Slf4j
@Component
public class JwtTokenProvider {

  // ------------------------------------------------
  // 1. 설정값 주입 및 초기화
  // ------------------------------------------------
  private final SecretKey secretKey;
  private final long accessTokenExpiration;
  private final long refreshTokenExpiration;

  /** 생성자 : application.yml의 JWT 설정을 주입받아 초기화 */
  public JwtTokenProvider(
      // application.yml의 jwt secret 값
      // -> UTF-8 바이트 배열로 변환 후 HMAC-SHA256 전용 SecretKey 객체 생성
      // -> 실제 운영 환경에서는 환경변수/Vault로 관리
      @Value("${jwt.secret}") String secret,
      @Value("${jwt.access-token-expiration}")
          long accessTokenExpiration, // Access Token 유효 기간 (밀리 초)
      @Value("${jwt.refresh-token-expiration}")
          long refreshTokenExpiration // Refresh Token 유효 기간 (밀리 초)
      ) {
    // Secret Key를 HMAC-SHA256용 SecretKey 객체로 변환
    // UTF-8 바이트 배열로 변환 후 Keys.hmacShaKeyFor()로 키 생성
    this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    this.accessTokenExpiration = accessTokenExpiration;
    this.refreshTokenExpiration = refreshTokenExpiration;
  }

  // ------------------------------------------------
  // 2. 토큰 생성 (Access / Refresh)
  // ------------------------------------------------

  /** Access Token 생성 */
  public String createAccessToken(Long userId, String username, String role) {
    Date now = new Date();
    Date validity = new Date(now.getTime() + accessTokenExpiration); // 만료 시간 = 현재 + 유효기간

    return Jwts.builder()
        .subject(username) // sub: Spring Security principal로 사용되는 사용자명
        .claim("userId", userId)
        .claim("role", role)
        .issuedAt(now) // iat: 발행 시간
        .expiration(validity) // exp: 만료 시간
        .signWith(secretKey) // HMAC-SHA256으로 서명 (secretKey 타입에서 알고리즘 자동 결정)
        .compact(); // header.payload.signature 형태의 문자열로 직렬화
  }

  /** Refresh Token 생성 */
  // Access Token 재발급 시 사용자 식별 용도로만 사용하므로 role 미포함
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

  // ------------------------------------------------
  // 3. 토큰 정보 추출
  // ------------------------------------------------

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

  // AuthService에서 @Value 없이 만료 시간 사용하기 위한 메서드
  public long getAccessTokenExpirationSeconds() {
    return accessTokenExpiration / 1000;
  }

  // RefreshToken 엔티티의 expiresAt 세팅에 사용
  public long getRefreshTokenExpirationSeconds() {
    return refreshTokenExpiration / 1000;
  }

  // ------------------------------------------------
  // 4. 토큰 검증
  // ------------------------------------------------

  /**
   * 토큰 유효성 검증
   *
   * <p>parseClaims() 내부에서 아래 항목을 자동으로 검증: 1. 서명 유효성 : secretKey로 HMAC-SHA256 서명 검증 2. 만료 여부 : exp
   * 클레임과 현재 시간 비교 3. 토큰 구조 : header.payload.signature 형식 확인
   *
   * <p>각 예외별 로그만 남기고 false 반환 (-> 예외를 상위로 전파하지 않음) -> JwtAuthenticationFilter에서 단순 true/false로 분기
   * 처리 가능
   */
  public boolean validateToken(String token) {
    try {
      parseClaims(token); // 정상 파싱되면 유효한 토큰
      return true;
    } catch (SecurityException | MalformedJwtException e) {
      log.error("Invalid JWT signature: {}", e.getMessage()); // 서명 불일치 또는 토큰 형식 오류
    } catch (ExpiredJwtException e) {
      log.error("Expired JWT token: {}", e.getMessage()); // 만료된 토큰
    } catch (UnsupportedJwtException e) {
      log.error("Unsupported JWT token: {}", e.getMessage()); // 지원하지 않는 토큰 형식
    } catch (IllegalArgumentException e) {
      log.error("JWT claims string is empty: {}", e.getMessage()); // 토큰이 null 또는 빈 문자열
    }
    return false;
  }

  /**
   * 토큰 만료 여부 확인
   *
   * <p>만료 여부만 단독으로 확인
   */
  public boolean isTokenExpired(String token) {
    try {
      Claims claims = parseClaims(token);
      return claims.getExpiration().before(new Date()); // 만료 시간이 현재보다 이전이면 만료
    } catch (ExpiredJwtException e) {
      return true; // 파싱 자체가 실패할 정도로 만료됨
    }
  }

  // ------------------------------------------------
  // 5. 내부 유틸
  // ------------------------------------------------

  /**
   * 토큰 파싱 및 Claims 반환 (내부 공통 메서드) - 모든 토큰 정보 추출 메서드와 검증 메서드에서 공통으로 사용
   *
   * <p>[처리 순서] 1. verifyWith(secretKey): HMAC-SHA256으로 서명 검증 2. parseSignedClaims(token): Base64
   * 디코딩 후 파싱 3. getPayload(): 검증된 Claims(payload) 반환
   *
   * <p>- private로 선언하여 외부에서 직접 호출 불가 - 예외는 호출한 public 메서드에서 처리
   */
  private Claims parseClaims(String token) {
    return Jwts.parser()
        .verifyWith(secretKey) // 서명 검증에 사용할 키 설정
        .build()
        .parseSignedClaims(token) // 서명 검증 + 파싱 동시 수행
        .getPayload(); // 검증된 Claims(payload) 반환
  }
}
