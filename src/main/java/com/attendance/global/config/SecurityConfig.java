package com.attendance.global.config;

import com.attendance.global.security.JwtAccessDeniedHandler;
import com.attendance.global.security.JwtAuthenticationEntryPoint;
import com.attendance.global.security.JwtAuthenticationFilter;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Spring Security 전역 설정 클래스
 *
 * <p>[주요 설정 항목] 1. CSRF : 비활성화 (JWT 사용으로 불필요) 2. CORS : 허용 Origin, Method, Header 설정 3. Session :
 * STATELESS (JWT 기반이므로 서버 세션 미사용) 4. 예외 처리 : 401 -> JwtAuthenticationEntryPoint 403 ->
 * JwtAccessDeniedHandler 5. 인가 규칙 : Public API / ADMIN 전용 / 인증 필요 경로 분리 6. Filter :
 * JwtAuthenticationFilter를 UsernamePasswordAuthenticationFilter 앞에 등록
 */
@Configuration
@EnableWebSecurity // Spring Security 활성화, 기본 Security 자동 설정 대체
@EnableMethodSecurity // @PreAuthorize, @PostAuthorize 등 메서드 레벨 인가 활성화
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter; // 요청마다 JWT 검증
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // 401 처리
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler; // 403 처리

  // application-local.yml의 cors.allowed-origins 값 주입
  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * Security Filter Chain 설정 - Spring Security의 핵심 설정으로, 모든 HTTP 요청이 이 체인을 통과
   *
   * <p>[순서] 요청 -> CorsFilter : CORS 검증 -> JwtAuthenticationFilter : JWT 검증 및 SecurityContext 세팅 ->
   * UsernamePasswordAuthenticationFilter : JWT 사용으로 실질적 미사용 -> AuthorizationFilter : 인가 규칙 적용 ->
   * 컨트롤러
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // CSRF 비활성화
        // JWT는 요청마다 토큰을 직접 검증 (CSRF 토큰 방식은 세션 기반 인증에서 필요)
        .csrf(AbstractHttpConfigurer::disable)

        // CORS 설정 (corsConfigurationSource 빈 참조)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))

        // 세션 STATELESS 설정 (JWT 사용 -> 서버가 세션을 저장하지 않음)
        // 매 요청마다 토큰으로만 인증 처리
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

        // 예외 처리 핸들러 등록
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 401: 인증 실패
                    .accessDeniedHandler(jwtAccessDeniedHandler) // 403: 인가 실패
            )

        // 요청 인증/인가 설정
        .authorizeHttpRequests(
            auth ->
                auth
                    // Public API : 인증 불필요
                    .requestMatchers("/api/auth/login", "/api/auth/refresh")
                    .permitAll()

                    // ADMIN 전용 API
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")

                    // 이 외의 요청은 인증 필요
                    .anyRequest()
                    .authenticated())

        // JWT 필터 추가
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /**
   * 비밀번호 암호화 인코더 빈 등록
   *
   * <p>BCrypt: 단방향 해시 알고리즘, salt 자동 적용 - 같은 비밀번호라도 매번 다른 해시값 생성 -> Rainbow Table 공격 방어 -
   * UserSrvice에서 회원가입 시 비밀번호 암호화, 로그인 시 검증에 적용
   */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /**
   * CORS(Cross-Origin Resource Sharing) 설정 빈 등록
   *
   * <p>브라우저의 동일 출처 정책(Same-Origin Policy)을 제어, 허용된 Origin에서 API 호출 가능
   */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    // 허용할 클라이언트 Origin (application-local.yml cors.allowed-origins 설정값 사용)
    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));

    // 허용할 HTTP 메서드
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    // 모든 헤더 적용 (Authorization: Bearer {token} 헤더 포함)
    configuration.setAllowedHeaders(List.of("*"));
    // 자격증명(쿠키·인증 헤더) 포함 요청 허용
    configuration.setAllowCredentials(true);
    // Preflight 요청 캐시 시간 (초)
    configuration.setMaxAge(3600L);

    // 모든 경로("/**")에서 위 CORS 설정 적용
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
