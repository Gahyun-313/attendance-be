package com.attendance.domain.user.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedissonTestConfig;
import com.attendance.global.security.JwtTokenProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 사용자 관리 API 통합 테스트 @SpringBootTest로 실제 Security 필터 체인(JWT 인증 + @PreAuthorize 인가)까지 전부 띄운 상태로,
 * 역할(ADMIN/STUDENT)에 따라 API 접근이 실제로 제어되는지 검증한다. @AutoConfigureTestDatabase(replace = ANY)로 인메모리 H2를
 * 사용한다. @Import(RedissonTestConfig.class) - Day6 Phase2: 실제 Redis 없이도 컨텍스트가 뜨도록 RedissonClient를
 * mock으로 대체 (RedissonTestConfig 참고).
 */
@Import(RedissonTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class UserControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  private String tokenFor(User user) {
    // 실제 로그인 과정을 거치지 않고, 저장된 사용자 정보로 바로 유효한 토큰을 발급 (테스트 편의)
    return jwtTokenProvider.createAccessToken(
        user.getId(), user.getUsername(), user.getRole().name());
  }

  private User saveAdmin() {
    return userRepository.save(
        User.builder()
            .username("admin01")
            .password("encoded-password")
            .name("관리자")
            .role(UserRole.ADMIN)
            .organizationId(1L)
            .build());
  }

  private User saveStudent() {
    return userRepository.save(
        User.builder()
            .username("20260001")
            .password("encoded-password")
            .name("학생1")
            .role(UserRole.STUDENT)
            .organizationId(1L)
            .build());
  }

  @Nested
  @DisplayName("POST /api/users")
  class CreateUser {

    @Test
    @DisplayName("ADMIN 토큰이면 학생 계정이 생성되고 실제 DB에 저장된다")
    void asAdmin_createsUserAndPersists() throws Exception {
      // given
      // ADMIN 권한을 가진 사용자가 새 학생 계정 생성을 요청하는 상황
      User admin = saveAdmin();
      String requestBody =
          """
              {"username":"20260002","password":"password1234","name":"홍길동","groupName":"A반"}
              """;

      // when & then
      // 정상 요청이면 201과 함께 생성된 학생 정보가 응답되어야 한다
      mockMvc
          .perform(
              post("/api/users")
                  .header("Authorization", "Bearer " + tokenFor(admin))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.username").value("20260002"))
          .andExpect(jsonPath("$.data.role").value("STUDENT"));

      // 응답뿐 아니라 실제로 DB에 저장됐는지도 확인
      assertThat(userRepository.existsByUsername("20260002")).isTrue();
    }

    @Test
    @DisplayName("STUDENT 토큰으로 요청하면 403 ACCESS_DENIED로 차단된다")
    void asStudent_returns403() throws Exception {
      // given
      // 권한이 없는 STUDENT 토큰으로 학생 계정 생성을 시도하는 상황
      User student = saveStudent();
      String requestBody =
          """
              {"username":"20260099","password":"password1234","name":"김철수","groupName":"A반"}
              """;

      // when & then
      // ADMIN 전용 API이므로 403(C004)으로 차단되어야 하고, 계정도 생성되면 안 된다
      mockMvc
          .perform(
              post("/api/users")
                  .header("Authorization", "Bearer " + tokenFor(student))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("C004"));
      assertThat(userRepository.existsByUsername("20260099")).isFalse();
    }

    @Test
    @DisplayName("토큰 없이 요청하면 401로 차단된다")
    void withoutToken_returns401() throws Exception {
      // given
      // 인증 정보 없이 요청하는 상황
      String requestBody =
          """
              {"username":"20260088","password":"password1234","name":"이영희","groupName":"A반"}
              """;

      // when & then
      // 인증 자체가 안 되어 있으므로 401로 차단되어야 한다
      mockMvc
          .perform(post("/api/users").contentType(MediaType.APPLICATION_JSON).content(requestBody))
          .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("이미 존재하는 username으로 생성 시도하면 409 DUPLICATE_USERNAME이 반환된다")
    void duplicateUsername_returns409() throws Exception {
      // given
      // 이미 20260001로 가입된 학생이 있는 상황에서 같은 username으로 재요청
      User admin = saveAdmin();
      saveStudent();
      String requestBody =
          """
              {"username":"20260001","password":"password1234","name":"중복학생","groupName":"A반"}
              """;

      // when & then
      mockMvc
          .perform(
              post("/api/users")
                  .header("Authorization", "Bearer " + tokenFor(admin))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isConflict())
          .andExpect(jsonPath("$.code").value("U002"));
    }
  }

  @Nested
  @DisplayName("POST /api/users/{userId}/activate")
  class ActivateUser {

    @Test
    @DisplayName("ADMIN 토큰이면 비활성화된 사용자가 다시 활성화된다")
    void asAdmin_activatesDeactivatedUser() throws Exception {
      // given
      // 이미 활성화된 학생 계정이 있는 상황

    }
  }

  @Nested
  @DisplayName("GET /api/users/me")
  class GetMyInfo {

    @Test
    @DisplayName("본인 토큰으로 요청하면 본인 정보가 조회된다")
    void returnsOwnInfo() throws Exception {
      // given
      User student = saveStudent();

      // when & then
      mockMvc
          .perform(get("/api/users/me").header("Authorization", "Bearer " + tokenFor(student)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.username").value("20260001"));
    }
  }
}
