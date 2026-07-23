package com.attendance.domain.user.controller;

import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 단체(organizationId) 간 데이터 격리 통합 테스트
 *
 * <p>서로 다른 단체(organizationId=1, 2)에 각각 관리자/학생/세션을 만들어두고, 한쪽 단체의 관리자 토큰으로 조회했을 때 다른
 * 단체의 데이터가 전혀 섞여 나오지 않는지 검증한다. UserService/SessionService의 organizationId 필터링 및 상세 조회
 * 시 소속 불일치를 404로 처리하는 로직(getUser/getSession)에 대한 회귀 테스트 성격이다.
 *
 * @SpringBootTest로 실제 Security 필터 체인까지 띄운 상태에서 검증한다.
 * @AutoConfigureTestDatabase(replace = ANY)로 인메모리 H2를 사용한다.
 * @Import(RedissonTestConfig.class) - AttendanceService가 컨텍스트에 뜨는 이상 RedissonClient 빈이
 * 필요해서 실제 Redis 없이도 컨텍스트가 뜨도록 mock으로 대체 (RedissonTestConfig 참고).
 */
@Import(RedissonTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class OrganizationIsolationIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private SessionRepository sessionRepository;
    @Autowired private JwtTokenProvider jwtTokenProvider;

    private static final Long ORG_A = 1L;
    private static final Long ORG_B = 2L;

    private String tokenFor(User user) {
        // 실제 로그인 과정을 거치지 않고, 저장된 사용자 정보로 바로 유효한 토큰을 발급 (테스트 편의)
        return jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    }

    private User saveAdmin(String username, Long organizationId) {
        return userRepository.save(
                User.builder()
                        .username(username)
                        .password("encoded-password")
                        .name("관리자")
                        .role(UserRole.ADMIN)
                        .organizationId(organizationId)
                        .build());
    }

    private User saveStudent(String username, String groupName, Long organizationId) {
        return userRepository.save(
                User.builder()
                        .username(username)
                        .password("encoded-password")
                        .name("학생")
                        .role(UserRole.STUDENT)
                        .groupName(groupName)
                        .organizationId(organizationId)
                        .build());
    }

    private AttendanceSession saveSession(String title, Long createdBy, Long organizationId) {
        return sessionRepository.save(
                AttendanceSession.builder()
                        .organizationId(organizationId)
                        .title(title)
                        .startTime(LocalDateTime.now().minusHours(1))
                        .endTime(LocalDateTime.now().plusHours(1))
                        .lateThresholdMinutes(10)
                        .status(SessionStatus.SCHEDULED)
                        .createdBy(createdBy)
                        .build());
    }

    @Nested
    @DisplayName("GET /api/users")
    class GetUsers {

        @Test
        @DisplayName("단체 A 관리자로 조회하면 단체 B 학생은 목록에 나오지 않는다")
        void doesNotLeakOtherOrganizationStudents() throws Exception {
            // given
            // 단체 A에 관리자 1명 + 학생 1명, 단체 B에 학생 1명이 있는 상황
            User adminA = saveAdmin("admin-a", ORG_A);
            saveStudent("student-a", "A반", ORG_A);
            saveStudent("student-b", "B반", ORG_B);

            // when & then
            // 단체 A 관리자 토큰으로 조회하면 단체 A 학생만 보여야 한다
            mockMvc.perform(get("/api/users").header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].username").value("student-a"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/{userId}")
    class GetUser {

        @Test
        @DisplayName("다른 단체 사용자를 상세 조회하면 404로 처리된다 (존재 여부 노출 방지)")
        void crossOrganizationDetail_returns404() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            User studentB = saveStudent("student-b", "B반", ORG_B);

            // when & then
            mockMvc.perform(
                            get("/api/users/{userId}", studentB.getId())
                                    .header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("U001"));
        }
    }

    @Nested
    @DisplayName("GET /api/users/groups")
    class GetGroups {

        @Test
        @DisplayName("단체 A 관리자로 조회하면 단체 B의 그룹명은 섞이지 않는다")
        void doesNotLeakOtherOrganizationGroupNames() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            saveStudent("student-a", "A반", ORG_A);
            saveStudent("student-b", "B반", ORG_B);

            // when & then
            mockMvc.perform(get("/api/users/groups").header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(1))
                    .andExpect(jsonPath("$.data[0]").value("A반"));
        }
    }

    @Nested
    @DisplayName("GET /api/sessions")
    class GetSessions {

        @Test
        @DisplayName("단체 A 관리자로 조회하면 단체 B 세션은 목록에 나오지 않는다")
        void doesNotLeakOtherOrganizationSessions() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            User adminB = saveAdmin("admin-b", ORG_B);
            saveSession("단체 A 세션", adminA.getId(), ORG_A);
            saveSession("단체 B 세션", adminB.getId(), ORG_B);

            // when & then
            mockMvc.perform(get("/api/sessions").header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content.length()").value(1))
                    .andExpect(jsonPath("$.data.content[0].title").value("단체 A 세션"));
        }
    }

    @Nested
    @DisplayName("GET /api/sessions/{sessionId}")
    class GetSession {

        @Test
        @DisplayName("다른 단체 세션을 상세 조회하면 404로 처리된다 (존재 여부 노출 방지)")
        void crossOrganizationDetail_returns404() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            User adminB = saveAdmin("admin-b", ORG_B);
            AttendanceSession sessionB = saveSession("단체 B 세션", adminB.getId(), ORG_B);

            // when & then
            mockMvc.perform(
                            get("/api/sessions/{sessionId}", sessionB.getId())
                                    .header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("S001"));
        }
    }

    @Nested
    @DisplayName("POST /api/sessions/{sessionId}/start")
    class StartSession {

        @Test
        @DisplayName("다른 단체 세션을 시작하려 하면 404로 차단된다")
        void crossOrganizationStart_returns404() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            User adminB = saveAdmin("admin-b", ORG_B);
            AttendanceSession sessionB = saveSession("단체 B 세션", adminB.getId(), ORG_B);

            // when & then
            // 세션 자체는 존재하지만 다른 단체 소속이므로, 있는지조차 모르게 404로 응답해야 한다
            mockMvc.perform(
                            post("/api/sessions/{sessionId}/start", sessionB.getId())
                                    .header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("S001"));
        }
    }

    @Nested
    @DisplayName("DELETE /api/sessions/{sessionId}")
    class DeleteSession {

        @Test
        @DisplayName("다른 단체 세션을 삭제하려 하면 404로 차단되고 실제로 삭제되지 않는다")
        void crossOrganizationDelete_returns404AndPersists() throws Exception {
            // given
            User adminA = saveAdmin("admin-a", ORG_A);
            User adminB = saveAdmin("admin-b", ORG_B);
            AttendanceSession sessionB = saveSession("단체 B 세션", adminB.getId(), ORG_B);

            // when
            mockMvc.perform(
                            delete("/api/sessions/{sessionId}", sessionB.getId())
                                    .header("Authorization", "Bearer " + tokenFor(adminA)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("S001"));

            // then - 차단만 되고 응답은 404였는지뿐 아니라, 실제로 삭제 안 됐는지도 확인
            assertThat(sessionRepository.existsById(sessionB.getId())).isTrue();
        }
    }
}
