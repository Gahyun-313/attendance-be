package com.attendance.domain.attendance.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedissonTestConfig;
import com.attendance.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세션 시작 → 체크인 → 대시보드로 이어지는 핵심 출석 흐름 통합 테스트 @SpringBootTest로 컨트롤러 → 서비스 → 리포지토리 → DB까지 전체 계층을 실제로
 * 연결해 검증한다. Phase 1-B에서 겪었던 "동일 태그 중복 ACTIVE 세션" 버그, WAITING 사전생성/체크인 갱신 로직이 실제 HTTP 흐름에서도 정확히 맞물려
 * 동작하는지 확인하는 최종 회귀 테스트 성격이다. @AutoConfigureTestDatabase(replace = ANY)로 인메모리 H2를 사용한다.
 *
 * <p>[Day6 Phase1 추가] 이 테스트는 대시보드 조회(GET .../dashboard)를 두 번 호출하는데, 실제 프로필(local)이
 * spring.cache.type: redis를 쓰기 때문에 그대로 두면 이 테스트가 로컬 Redis 서버가 떠 있어야만 통과하는 테스트가 돼버린다 - DB 흐름을 검증하는 게
 * 목적이지 캐싱 자체를 검증하는 게 아니므로, spring.cache.type을 none으로 덮어써서 이 테스트만큼은 캐시 없이(매번 새로 계산해서) 동작하게 만들었다.
 *
 * <p>[Day6 Phase2 추가] redisson-spring-boot-starter를 추가하면서 checkIn()이 이제 실제 RedissonClient로 분산 락을
 * 시도한다. spring.cache.type과 달리 이건 끄는 프로퍼티가 따로 없어서(Redisson은 클래스패스에 있으면 무조건 자동 설정됨),
 * RedissonTestConfig를 @Import해 실제 Redis 연결 없이도 체크인 흐름을 검증할 수 있게 했다. 다른 @SpringBootTest는 이 mock을 그냥
 * 컨텍스트가 뜨게만 하는 용도로 쓰지만, 여기는 실제로 checkIn()을 호출하므로 아래 @BeforeEach에서 tryLock() 등을 "항상 성공"으로 추가 스텁한다.
 */
@Import(RedissonTestConfig.class)
@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@TestPropertySource(properties = "spring.cache.type=none")
@Transactional
class AttendanceFlowIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private NfcTagRepository nfcTagRepository;
  @Autowired private SessionRepository sessionRepository;
  @Autowired private JwtTokenProvider jwtTokenProvider;

  // RedissonTestConfig가 제공하는 mock 빈(@Primary) - 아래 @BeforeEach에서 락 관련 동작을 추가로 스텁한다
  @Autowired private RedissonClient redissonClient;

  private String tokenFor(User user) {
    // 실제 로그인 과정을 거치지 않고, 저장된 사용자 정보로 바로 유효한 토큰을 발급 (테스트 편의)
    return jwtTokenProvider.createAccessToken(
        user.getId(), user.getUsername(), user.getRole().name());
  }

  @BeforeEach
  void setUpDistributedLockStub() throws InterruptedException {
    // checkIn()의 findOrCreateRecordWithLock()이 redissonClient.getLock(...).tryLock(...)을 호출하므로,
    // 이 테스트가 실제 Redis 서버 없이도 항상 락을 즉시 획득한 것처럼 동작하도록 미리 스텁해둔다.
    RLock lock = Mockito.mock(RLock.class);
    Mockito.when(redissonClient.getLock(ArgumentMatchers.anyString())).thenReturn(lock);
    // leaseTime을 명시하지 않는 2-인자 tryLock(waitTime, unit)으로 호출하도록 바뀜(워치독 활성화) - 자세한
    // 이유는 AttendanceService.findOrCreateRecordWithLock() 주석 참고
    Mockito.when(lock.tryLock(ArgumentMatchers.anyLong(), ArgumentMatchers.any(TimeUnit.class)))
        .thenReturn(true);
    Mockito.when(lock.isHeldByCurrentThread()).thenReturn(true);
  }

  @Test
  @DisplayName("세션을 시작하면 그룹 학생에게 WAITING이 생기고, 체크인하면 갱신되며, 대시보드에 정확히 집계된다")
  void fullFlow_startCheckInDashboard() throws Exception {
    // given
    // ADMIN 1명, A반 학생 2명, 그 그룹을 대상으로 하는 SCHEDULED 세션 하나를 준비한 상황
    User admin =
        userRepository.save(
            User.builder()
                .username("admin01")
                .password("encoded-password")
                .name("관리자")
                .role(UserRole.ADMIN)
                .organizationId(1L)
                .build());
    User student1 =
        userRepository.save(
            User.builder()
                .username("20260001")
                .password("encoded-password")
                .name("학생1")
                .role(UserRole.STUDENT)
                .groupName("A반")
                .organizationId(1L)
                .build());
    // student2는 별도로 참조하지 않고, A반에 체크인 안 한 학생이 존재하는 상황만 만들면 됨
    userRepository.save(
        User.builder()
            .username("20260002")
            .password("encoded-password")
            .name("학생2")
            .role(UserRole.STUDENT)
            .groupName("A반")
            .organizationId(1L)
            .build());
    NfcTag tag =
        nfcTagRepository.save(
            NfcTag.builder()
                    .organizationId(1L)
                    .uid("TAG-001").
                    name("A반 태그").
                    status(NfcTagStatus.ACTIVE).
                    build());
    AttendanceSession session =
        sessionRepository.save(
            AttendanceSession.builder()
                .organizationId(1L)
                .title("A반 1교시")
                .groupName("A반")
                .sessionDate(LocalDateTime.now().toLocalDate())
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .lateThresholdMinutes(10)
                .status(SessionStatus.SCHEDULED)
                .nfcTag(tag)
                .createdBy(admin.getId())
                .build());

    // when & then - 1) 세션 시작 시 그룹 학생 전원에게 WAITING이 사전 생성되어야 함
    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/start", session.getId())
                .header("Authorization", "Bearer " + tokenFor(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("ACTIVE"));

    // when & then - 2) 학생1이 체크인하면 사전 생성된 WAITING이 PRESENT로 갱신되어야 함 (신규 레코드 아님)
    String checkInBody =
        """
                {"nfcTagUid":"TAG-001"}
                """;
    mockMvc
        .perform(
            post("/api/attendances/check-in")
                .header("Authorization", "Bearer " + tokenFor(student1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.data.status").value("PRESENT"));

    // when & then - 3) 학생1은 이미 체크인했으므로 재시도하면 중복 출석으로 차단되어야 함
    mockMvc
        .perform(
            post("/api/attendances/check-in")
                .header("Authorization", "Bearer " + tokenFor(student1))
                .contentType(MediaType.APPLICATION_JSON)
                .content(checkInBody))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("AT002"));

    // when & then - 4) 학생2는 아직 체크인 전이므로 대시보드에는 targetCount=2, present=1, waiting=1이어야 함
    mockMvc
        .perform(
            get("/api/attendances/sessions/{sessionId}/dashboard", session.getId())
                .header("Authorization", "Bearer " + tokenFor(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.targetCount").value(2))
        .andExpect(jsonPath("$.data.present").value(1))
        .andExpect(jsonPath("$.data.waiting").value(1));

    // when & then - 5) 세션을 종료하면 학생2의 남은 WAITING이 ABSENT로 자동 전환되어야 함
    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/close", session.getId())
                .header("Authorization", "Bearer " + tokenFor(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.status").value("COMPLETED"));

    mockMvc
        .perform(
            get("/api/attendances/sessions/{sessionId}/dashboard", session.getId())
                .header("Authorization", "Bearer " + tokenFor(admin)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.present").value(1))
        .andExpect(jsonPath("$.data.absent").value(1))
        .andExpect(jsonPath("$.data.waiting").value(0));
  }

  @Test
  @DisplayName("동일 NFC 태그로 다른 세션을 시작하려 하면 409 NFC_TAG_ALREADY_IN_USE로 차단된다")
  void startSession_duplicateTagInUse_returns409() throws Exception {
    // given
    // 같은 태그를 쓰는 세션 A가 이미 ACTIVE인 상태에서, 같은 태그의 세션 B를 시작하려는 상황
    User admin =
        userRepository.save(
            User.builder()
                .username("admin02")
                .password("encoded-password")
                .name("관리자2")
                .role(UserRole.ADMIN)
                .organizationId(1L)
                .build());
    NfcTag tag =
        nfcTagRepository.save(
            NfcTag.builder()
                    .organizationId(1L)
                    .uid("TAG-002")
                    .name("공용 태그")
                    .status(NfcTagStatus.ACTIVE)
                    .build());
    // sessionA는 별도로 참조하지 않고, 같은 태그를 쓰는 다른 ACTIVE 세션이 존재하는 상황만 만들면 됨
    sessionRepository.save(
        AttendanceSession.builder()
            .organizationId(1L)
            .title("세션 A")
            .startTime(LocalDateTime.now().minusMinutes(1))
            .endTime(LocalDateTime.now().plusHours(1))
            .lateThresholdMinutes(10)
            .status(SessionStatus.ACTIVE)
            .nfcTag(tag)
            .createdBy(admin.getId())
            .build());
    AttendanceSession sessionB =
        sessionRepository.save(
            AttendanceSession.builder()
                .organizationId(1L)
                .title("세션 B")
                .startTime(LocalDateTime.now().minusMinutes(1))
                .endTime(LocalDateTime.now().plusHours(1))
                .lateThresholdMinutes(10)
                .status(SessionStatus.SCHEDULED)
                .nfcTag(tag)
                .createdBy(admin.getId())
                .build());

    // when & then
    mockMvc
        .perform(
            post("/api/sessions/{sessionId}/start", sessionB.getId())
                .header("Authorization", "Bearer " + tokenFor(admin)))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("S004"));
  }
}
