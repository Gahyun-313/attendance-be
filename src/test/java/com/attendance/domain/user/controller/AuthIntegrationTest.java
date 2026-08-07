package com.attendance.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.RefreshTokenRepository;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedissonTestConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인/인증 통합 테스트 @SpringBootTest로 전체 Spring 컨텍스트(Security 필터 체인, GlobalExceptionHandler 포함)를 실제로
 * 띄우고, MockMvc로 진짜 HTTP 요청/응답 사이클을 검증한다. 단위 테스트와 달리 JWT 발급/검증까지 실제로
 * 동작한다. @AutoConfigureTestDatabase(replace = ANY)로 application-local.yml의 실제 MySQL 설정 대신 인메모리 H2를
 * 강제로 사용하도록 한다 (로컬 개발 DB에 영향 없음). @Import(RedissonTestConfig.class) - Day6 Phase2: 이 테스트는 로그인만 검증하고
 * 체크인은 안 타지만, AttendanceService가 컨텍스트에 뜨는 이상 RedissonClient 빈이 필요해서 실제 Redis 없이도 컨텍스트가 뜨도록 mock으로
 * 대체해둔다 (RedissonTestConfig 참고).
 */
@Import(RedissonTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional // 각 테스트 종료 후 자동 롤백되어 테스트 간 데이터가 섞이지 않음
class AuthIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private PasswordEncoder passwordEncoder;
  @Autowired private RefreshTokenRepository refreshTokenRepository;

  // 비밀번호 재설정 테스트에서 실제 Gmail 발송을 막기 위한 mock
  // EmailVerificationService가 이 빈을 주입받는다
  @MockitoBean private JavaMailSender mailSender;

  // 테스트 메서드마다 mailSender 호출 이력이 누적되지 않도록 초기화 (Spring 컨텍스트가 캐싱되어 mock이 재사용됨)
  @AfterEach
  void resetMailMock() {
    Mockito.reset(mailSender);
  }

  private User saveUser(String username, String rawPassword, UserRole role) {
    // 로그인 테스트용 사용자 사전 생성 (실제 암호화된 비밀번호로 저장)
    User user =
        User.builder()
            .username(username)
            .password(passwordEncoder.encode(rawPassword))
            .name("테스트 사용자")
            .role(role)
            .organizationId(1L)
            .build();
    return userRepository.save(user);
  }

  private User saveUserWithEmail(String username, String rawPassword, String email) {
    // 이메일 인증으로 가입한 ADMIN 사용자의 비밀번호 재설정 테스트용
    User user =
        User.builder()
            .username(username)
            .password(passwordEncoder.encode(rawPassword))
            .email(email)
            .name("테스트 사용자")
            .role(UserRole.ADMIN)
            .organizationId(1L)
            .build();
    return userRepository.save(user);
  }

  private User saveOAuthUser(String username, String email) {
    // 소셜 로그인 전용 계정 - password가 없어 비밀번호 재설정 대상이 아님을 검증하는 데 사용
    User user =
        User.builder()
            .username(username)
            .password(null)
            .email(email)
            .name("소셜 계정")
            .role(UserRole.ADMIN)
            .organizationId(1L)
            .provider("GOOGLE")
            .providerId("google-sub-123")
            .build();
    return userRepository.save(user);
  }

  private String extractCode(SimpleMailMessage message) {
    // EmailVerificationService.sendResetEmail()
    // 본문 형식: "인증 코드: 123456\n5분 이내에..."
    String text = message.getText();
    String prefix = "인증 코드: ";
    int start = text.indexOf(prefix) + prefix.length();
    int end = text.indexOf("\n", start);
    return text.substring(start, end);
  }

  @Nested
  @DisplayName("POST /api/auth/login")
  class Login {

    @Test
    @DisplayName("아이디/비밀번호가 일치하면 로그인 성공하고 토큰이 발급된다")
    void success_returnsTokens() throws Exception {
      // given
      // 실제로 존재하는 사용자와 올바른 비밀번호로 로그인을 시도하는 상황
      saveUser("20260001", "password1234", UserRole.STUDENT);
      String requestBody =
          """
              {"username":"20260001","password":"password1234"}
              """;

      // when & then
      // 로그인 성공 시 accessToken/refreshToken이 함께 발급되어야 한다
      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
          .andExpect(jsonPath("$.data.user.username").value("20260001"));
    }

    @Test
    @DisplayName("비밀번호가 틀리면 401 INVALID_CREDENTIALS 예외가 발생한다")
    void wrongPassword_returns401() throws Exception {
      // given
      // 사용자는 존재하지만 잘못된 비밀번호로 로그인을 시도하는 상황
      saveUser("20260002", "correct-password", UserRole.STUDENT);
      String requestBody =
          """
              {"username":"20260002","password":"wrong-password"}
              """;

      // when & then
      // 비밀번호 불일치 시 401과 함께 INVALID_CREDENTIALS(A005) 코드가 반환되어야 한다
      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("A005"));
    }

    @Test
    @DisplayName("존재하지 않는 아이디로 로그인하면 401 INVALID_CREDENTIALS 예외가 발생한다")
    void unknownUsername_returns401() throws Exception {
      // given
      // 아예 존재하지 않는 username으로 로그인을 시도하는 상황
      String requestBody =
          """
              {"username":"no-such-user","password":"password1234"}
              """;

      // when & then
      // 존재하지 않는 사용자를 조회한 것과 같은 A005로 응답해야 함 (사용자 존재 여부를 노출하지 않기 위함)
      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("A005"));
    }

    @Test
    @DisplayName("비활성화된(active=false) 사용자는 로그인이 차단된다")
    void inactiveUser_returnsInactiveError() throws Exception {
      // given
      // 비밀번호는 맞지만 관리자에 의해 비활성화된 계정인 상황
      User user = saveUser("20260003", "password1234", UserRole.STUDENT);
      user.deactivate();
      userRepository.save(user);
      String requestBody =
          """
              {"username":"20260003","password":"password1234"}
              """;

      // when & then
      // 비활성 사용자는 U004(INACTIVE_USER)로 로그인이 차단되어야 한다
      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("U004"));
    }

    @Test
    @DisplayName("같은 사용자가 두 번 연속 로그인해도 500 없이 성공하고 refresh token은 1건만 남는다")
    void loginTwice_replacesRefreshTokenWithoutError() throws Exception {
      // given
      // 회귀 테스트: RefreshTokenRepository.deleteByUserId()가 @Modifying 없는 평범한 derived
      // delete였을 때, 재로그인 시 "옛 토큰 삭제(지연 flush)"보다 "새 토큰 저장(IDENTITY라 즉시 INSERT)"이
      // 먼저 반영되어 uk_refresh_tokens_user_id 제약 위반으로 500이 나던 실제 버그를 재현한다.
      saveUser("20260004", "password1234", UserRole.STUDENT);
      String requestBody =
          """
              {"username":"20260004","password":"password1234"}
              """;

      // when
      // 동일 계정으로 두 번 연속 로그인 - 두 번째 호출이 이 버그의 재현 지점
      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk());

      mockMvc
          .perform(
              post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
          .andExpect(jsonPath("$.data.refreshToken").isNotEmpty());

      // then
      // 두 번 로그인해도 해당 사용자의 refresh token은 (교체되어) 정확히 1건만 남아있어야 한다
      User user = userRepository.findByUsername("20260004").orElseThrow();
      assertThat(refreshTokenRepository.findByUserId(user.getId())).isPresent();
    }
  }

  @Nested
  @DisplayName("POST /api/auth/password-reset/request")
  class RequestPasswordReset {

    @Test
    @DisplayName("등록된 이메일로 요청하면 인증 코드가 발송된다")
    void success_sendsCode() throws Exception {
      // given
      saveUserWithEmail("reset01", "old-password1", "reset01@test.com");
      String requestBody =
          """
                  {"email":"reset01@test.com"}
                  """;

      // when & then
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isOk());

      // 실제 메일 발송(mailSender.send)이 정확히 한 번 호출됐는지 확인
      Mockito.verify(mailSender).send(Mockito.any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("등록되지 않은 이메일로 요청하면 404 USER_NOT_FOUND")
    void unknownEmail_returns404() throws Exception {
      // given
      String requestBody =
          """
                  {"email":"no-such-email@test.com"}
                  """;

      // when & then
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("U001"));
    }

    @Test
    @DisplayName("소셜 로그인 전용 계정 이메일로 요청하면 400 SOCIAL_ACCOUNT_NO_PASSWORD")
    void socialAccount_returns400() throws Exception {
      // given
      saveOAuthUser("google-admin", "social@test.com");
      String requestBody =
          """
                  {"email":"social@test.com"}
                  """;

      // when & then
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("O007"));
    }
  }

  @Nested
  @DisplayName("POST /api/auth/password-reset/verify")
  class VerifyPasswordReset {

    @Test
    @DisplayName("올바른 코드로 검증하면 비밀번호가 바뀌고, 새 비밀번호로만 로그인할 수 있다")
    void success_changesPasswordAndAllowsNewLogin() throws Exception {
      // given
      // 코드를 하드코딩할 수 없으니(매번 랜덤) 실제로 발송 단계를 거쳐 mock에 캡처된 값을 그대로 사용한다
      saveUserWithEmail("reset02", "old-password1", "reset02@test.com");
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                      {"email":"reset02@test.com"}
                                      """))
          .andExpect(status().isOk());

      ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
      Mockito.verify(mailSender).send(captor.capture());
      String code = extractCode(captor.getValue());

      // when & then - 1) 받은 코드로 검증 + 새 비밀번호 적용
      String verifyBody =
          String.format(
              """
                      {"email":"reset02@test.com","code":"%s","newPassword":"new-password1"}
                      """,
              code);
      mockMvc
          .perform(
              post("/api/auth/password-reset/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(verifyBody))
          .andExpect(status().isOk());

      // when & then - 2) 새 비밀번호로 로그인 성공
      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                      {"username":"reset02","password":"new-password1"}
                                      """))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.accessToken").isNotEmpty());

      // when & then - 3) 예전 비밀번호로는 더 이상 로그인 안 됨
      mockMvc
          .perform(
              post("/api/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                      {"username":"reset02","password":"old-password1"}
                                      """))
          .andExpect(status().isUnauthorized())
          .andExpect(jsonPath("$.code").value("A005"));
    }

    @Test
    @DisplayName("코드를 요청한 적 없이 검증하면 400 EMAIL_VERIFICATION_EXPIRED")
    void neverRequested_returns400() throws Exception {
      // given
      saveUserWithEmail("reset03", "old-password1", "reset03@test.com");
      String verifyBody =
          """
                  {"email":"reset03@test.com","code":"000000","newPassword":"new-password1"}
                  """;

      // when & then
      mockMvc
          .perform(
              post("/api/auth/password-reset/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(verifyBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("O005"));
    }

    @Test
    @DisplayName("코드가 틀리면 400 EMAIL_VERIFICATION_INVALID")
    void wrongCode_returns400() throws Exception {
      // given
      saveUserWithEmail("reset04", "old-password1", "reset04@test.com");
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                      {"email":"reset04@test.com"}
                                      """))
          .andExpect(status().isOk());

      // when & then - 실제 코드 대신 임의의 틀린 코드로 검증 시도
      String verifyBody =
          """
                  {"email":"reset04@test.com","code":"999999","newPassword":"new-password1"}
                  """;
      mockMvc
          .perform(
              post("/api/auth/password-reset/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(verifyBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("O006"));
    }

    @Test
    @DisplayName("같은 코드를 두 번 검증하면 두 번째는 실패한다 (1회용 소비)")
    void reusedCode_secondAttemptFails() throws Exception {
      // given
      saveUserWithEmail("reset05", "old-password1", "reset05@test.com");
      mockMvc
          .perform(
              post("/api/auth/password-reset/request")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      """
                                      {"email":"reset05@test.com"}
                                      """))
          .andExpect(status().isOk());

      ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
      Mockito.verify(mailSender).send(captor.capture());
      String code = extractCode(captor.getValue());
      String verifyBody =
          String.format(
              """
                      {"email":"reset05@test.com","code":"%s","newPassword":"new-password1"}
                      """,
              code);

      // when - 첫 번째 검증은 성공
      mockMvc
          .perform(
              post("/api/auth/password-reset/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(verifyBody))
          .andExpect(status().isOk());

      // then - 같은 코드로 두 번째 시도하면 이미 소비되어 EXPIRED로 실패
      mockMvc
          .perform(
              post("/api/auth/password-reset/verify")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(verifyBody))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("O005"));
    }
  }
}
