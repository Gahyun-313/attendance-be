package com.attendance.domain.notification.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attendance.domain.fcm.entity.FcmToken;
import com.attendance.domain.fcm.repository.FcmTokenRepository;
import com.attendance.domain.notification.entity.Notification;
import com.attendance.domain.notification.repository.NotificationRepository;
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
 * 알림 API 통합 테스트 @SpringBootTest로 실제 Security 필터 체인(JWT 인증 + @PreAuthorize 인가)까지 띄운 상태로, 알림 생성 시 실제
 * 발송 처리(SENT 판정) 및 역할별 목록 조회 범위, 취소 가능 여부를 HTTP 흐름으로 검증한다. @AutoConfigureTestDatabase(replace =
 * ANY)로 인메모리 H2를 사용한다.
 */
@Import(RedissonTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Transactional
class NotificationControllerIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private FcmTokenRepository fcmTokenRepository;
  @Autowired private NotificationRepository notificationRepository;
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
            .password("encoded")
            .name("관리자")
            .role(UserRole.ADMIN)
            .build());
  }

  private User saveStudent(String groupName) {
    return userRepository.save(
        User.builder()
            .username("student-" + groupName)
            .password("encoded")
            .name("학생")
            .role(UserRole.STUDENT)
            .groupName(groupName)
            .build());
  }

  @Nested
  @DisplayName("POST /api/notifications")
  class CreateNotification {

    @Test
    @DisplayName("대상 그룹에 FCM 토큰 등록자가 있으면 즉시 SENT로 생성된다")
    void withTokenHolder_createsAsSent() throws Exception {
      // given
      // A반 학생 1명이 FCM 토큰을 등록해둔 상태에서 A반 대상 알림을 생성
      User admin = saveAdmin();
      User student = saveStudent("A반");
      fcmTokenRepository.save(FcmToken.builder().userId(student.getId()).token("token-1").build());
      String requestBody =
          """
                    {"title":"공지","content":"내용입니다","targetGroup":"A반"}
                    """;

      // when & then
      mockMvc
          .perform(
              post("/api/notifications")
                  .header("Authorization", "Bearer " + tokenFor(admin))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.data.status").value("SENT"))
          .andExpect(jsonPath("$.data.targetCount").value(1));
    }

    @Test
    @DisplayName("STUDENT 토큰으로 요청하면 403 ACCESS_DENIED로 차단된다")
    void asStudent_returns403() throws Exception {
      // given
      User student = saveStudent("A반");
      String requestBody =
          """
                    {"title":"공지","content":"내용입니다","targetGroup":"A반"}
                    """;

      // when & then
      mockMvc
          .perform(
              post("/api/notifications")
                  .header("Authorization", "Bearer " + tokenFor(student))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(requestBody))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("C004"));
    }
  }

  @Nested
  @DisplayName("GET /api/notifications")
  class GetNotifications {

    @Test
    @DisplayName("STUDENT는 본인 그룹 대상의 발송완료 알림만 보인다")
    void student_seesOnlyOwnGroupSentNotifications() throws Exception {
      // given
      // A반 대상 발송완료 알림 1건, B반 대상 발송완료 알림 1건이 있는 상황에서 A반 학생이 조회
      User admin = saveAdmin();
      User studentA = saveStudent("A반");
      Notification sentForA =
          notificationRepository.save(
              Notification.builder()
                  .title("A반 공지")
                  .content("내용")
                  .targetGroup("A반")
                  .createdBy(admin.getId())
                  .build());
      sentForA.markSent(1);
      Notification sentForB =
          notificationRepository.save(
              Notification.builder()
                  .title("B반 공지")
                  .content("내용")
                  .targetGroup("B반")
                  .createdBy(admin.getId())
                  .build());
      sentForB.markSent(1);

      // when & then
      // A반 학생에게는 A반 공지만 보여야 하고, B반 공지는 노출되면 안 됨
      mockMvc
          .perform(
              get("/api/notifications").header("Authorization", "Bearer " + tokenFor(studentA)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.data.content.length()").value(1))
          .andExpect(jsonPath("$.data.content[0].title").value("A반 공지"));
    }
  }

  @Nested
  @DisplayName("DELETE /api/notifications/{notificationId}")
  class CancelNotification {

    @Test
    @DisplayName("이미 발송된 알림을 취소하면 400 NT002가 반환된다")
    void alreadySent_returns400() throws Exception {
      // given
      User admin = saveAdmin();
      Notification notification =
          notificationRepository.save(
              Notification.builder().title("공지").content("내용").createdBy(admin.getId()).build());
      notification.markSent(0);

      // when & then
      mockMvc
          .perform(
              delete("/api/notifications/" + notification.getId())
                  .header("Authorization", "Bearer " + tokenFor(admin)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.code").value("NT002"));
    }

    @Test
    @DisplayName("존재하지 않는 알림을 취소하면 404 NT001이 반환된다")
    void notFound_returns404() throws Exception {
      // given
      User admin = saveAdmin();

      // when & then
      mockMvc
          .perform(
              delete("/api/notifications/999999")
                  .header("Authorization", "Bearer " + tokenFor(admin)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.code").value("NT001"));
    }
  }
}
