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

/** Spring Security 전역 설정. CSRF 비활성화, JWT 기반 STATELESS 인증과 경로별 인가 규칙 적용. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity // @PreAuthorize 등 메서드 레벨 인가를 활성화한다.
@RequiredArgsConstructor
public class SecurityConfig {

  private final JwtAuthenticationFilter jwtAuthenticationFilter; // 요청마다 JWT 검증
  private final JwtAuthenticationEntryPoint jwtAuthenticationEntryPoint; // 401 처리 담당
  private final JwtAccessDeniedHandler jwtAccessDeniedHandler; // 403 처리 담당

  @Value("${cors.allowed-origins}")
  private String allowedOrigins;

  /**
   * 요청 필터 체인 구성
   * 요청 -> CorsFilter -> JwtAuthenticationFilter(JWT 검증) -> UsernamePasswordAuthenticationFilter
   * (JWT 방식이라 실질적으로 미사용) -> AuthorizationFilter -> 컨트롤러 순으로 처리된다.
   */
  @Bean
  public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http
        // JWT는 요청마다 토큰을 직접 검증하므로 세션 기반 인증에 쓰이는 CSRF 토큰 방식이 필요 없다.
        .csrf(AbstractHttpConfigurer::disable)
        .cors(cors -> cors.configurationSource(corsConfigurationSource()))
        // JWT 기반이라 서버가 세션을 저장하지 않고, 매 요청을 토큰만으로 인증한다.
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(
            exception ->
                exception
                    .authenticationEntryPoint(jwtAuthenticationEntryPoint) // 인증 실패 시 401 반환
                    .accessDeniedHandler(jwtAccessDeniedHandler) // 인가 실패 시 403 반환
            )
        .authorizeHttpRequests(
            auth ->
                auth
                    // 로그인/토큰 재발급은 인증 전에 호출되므로 허용한다.
                    .requestMatchers("/api/auth/login", "/api/auth/refresh")
                    .permitAll()
                    // 소셜 로그인/이메일 인증 조인은 로그인 전이라 토큰 없이 호출된다.
                    .requestMatchers(
                        "/api/auth/oauth/**",
                        "/api/auth/join/email/**",
                        "/api/auth/password-reset/**")
                    .permitAll()
                    // 브라우저 네이티브 WebSocket은 커스텀 헤더(Authorization)를 못 보내므로
                    // 여기선 permitAll로 통과시키고, StompHandshakeInterceptor가 쿼리 파라미터
                    // (?token=)로 받은 JWT를 별도 검증한다.
                    .requestMatchers("/ws/**")
                    .permitAll()
                    .requestMatchers("/api/admin/**")
                    .hasRole("ADMIN")
                    .anyRequest()
                    .authenticated())
        .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

    return http.build();
  }

  /** BCrypt로 비밀번호 해시. salt가 자동 적용돼 같은 비밀번호도 매번 다른 해시가 나와 Rainbow Table 공격을 방어한다. */
  @Bean
  public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();
  }

  /** 허용된 Origin에서만 API를 호출할 수 있도록 CORS 설정 */
  @Bean
  public CorsConfigurationSource corsConfigurationSource() {
    CorsConfiguration configuration = new CorsConfiguration();

    configuration.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
    configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
    configuration.setAllowedHeaders(List.of("*")); // Authorization 헤더 포함
    configuration.setAllowCredentials(true);
    configuration.setMaxAge(3600L); // Preflight 캐시 시간(초)

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);
    return source;
  }
}
