package com.attendance.domain.user.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인/인증 통합 테스트
 *
 * @SpringBootTest로 전체 Spring 컨텍스트(Security 필터 체인, GlobalExceptionHandler 포함)를 실제로 띄우고,
 * MockMvc로 진짜 HTTP 요청/응답 사이클을 검증한다. 단위 테스트와 달리 JWT 발급/검증까지 실제로 동작한다.
 *
 * @AutoConfigureTestDatabase(replace = ANY)로 application-local.yml의 실제 MySQL 설정 대신
 * 인메모리 H2를 강제로 사용하도록 한다 (로컬 개발 DB에 영향 없음).
 */
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional // 각 테스트 종료 후 자동 롤백되어 테스트 간 데이터가 섞이지 않음
class AuthIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private User saveUser(String username, String rawPassword, UserRole role) {
        // 로그인 테스트용 사용자 사전 생성 (실제 암호화된 비밀번호로 저장)
        User user =
                User.builder()
                        .username(username)
                        .password(passwordEncoder.encode(rawPassword))
                        .name("테스트 사용자")
                        .role(role)
                        .build();
        return userRepository.save(user);
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
            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
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
            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
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
            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
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
            mockMvc.perform(
                            post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("U004"));
        }
    }
}