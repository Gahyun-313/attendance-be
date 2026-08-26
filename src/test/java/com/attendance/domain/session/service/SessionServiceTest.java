package com.attendance.domain.session.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.attendance.domain.attendance.service.AttendanceService;
import com.attendance.domain.group.repository.GroupRepository;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.global.exception.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * SessionService 단위 테스트
 *
 * <p>Spring Context나 실제 DB를 띄우지 않고, Mockito로 Repository/Service들을 대체한다. SessionAutoCloseScheduler가
 * 호출하는 배치 전용 메서드(findExpiredActiveSessionIds/autoCloseSession/findExpiredScheduledSessionIds/autoCancelSession)만
 * 검증 대상으로 삼는다 - 기존 CRUD 메서드들은 이번 변경과 무관해 범위에서 제외한다.
 *
 * <p>검증하는 주요 정책: - 종료 시각이 지난 ACTIVE/SCHEDULED 세션 ID를 배치가 그대로 넘겨받는다 - ACTIVE 세션 자동 종료 시
 * 단체의 autoAbsentEnabled 설정(및 단체를 못 찾았을 때의 기본값 true)에 따라 자동 결석 처리 여부가 갈린다 - SCHEDULED 세션은 결석 처리 없이
 * 취소만 된다 - 존재하지 않는 세션 ID로 호출하면 EntityNotFoundException
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

  @Mock private SessionRepository sessionRepository;
  @Mock private NfcTagRepository nfcTagRepository;
  @Mock private AttendanceService attendanceService;
  @Mock private OrganizationRepository organizationRepository;
  @Mock private GroupRepository groupRepository;

  @InjectMocks private SessionService sessionService;

  private AttendanceSession activeSession;
  private AttendanceSession scheduledSession;

  @BeforeEach
  void setUp() {
    activeSession =
        AttendanceSession.builder()
            .id(1L)
            .organizationId(10L)
            .title("정기 세션")
            .status(SessionStatus.ACTIVE)
            .startTime(LocalDateTime.now().minusHours(2))
            .endTime(LocalDateTime.now().minusHours(1))
            .createdBy(1L)
            .build();

    scheduledSession =
        AttendanceSession.builder()
            .id(2L)
            .organizationId(10L)
            .title("미시작 세션")
            .status(SessionStatus.SCHEDULED)
            .startTime(LocalDateTime.now().minusHours(3))
            .endTime(LocalDateTime.now().minusHours(2))
            .createdBy(1L)
            .build();
  }

  @Nested
  @DisplayName("findExpiredActiveSessionIds()")
  class FindExpiredActiveSessionIds {

    @Test
    @DisplayName("종료 시각이 지난 ACTIVE 세션들의 ID 목록을 반환한다")
    void returnsIdsOfExpiredActiveSessions() {
      given(sessionRepository.findByStatusAndEndTimeBefore(eq(SessionStatus.ACTIVE), any(LocalDateTime.class)))
          .willReturn(List.of(activeSession));

      List<Long> result = sessionService.findExpiredActiveSessionIds();

      assertThat(result).containsExactly(1L);
    }
  }

  @Nested
  @DisplayName("autoCloseSession()")
  class AutoCloseSession {

    @Test
    @DisplayName("단체의 자동 결석 처리 설정이 켜져 있으면 세션을 종료하고 남은 WAITING을 결석 처리한다")
    void closesSessionAndMarksAbsent_whenAutoAbsentEnabled() {
      Organization organization = Organization.builder().name("테스트 단체").code("CODE1").active(true).build();
      given(sessionRepository.findById(1L)).willReturn(Optional.of(activeSession));
      given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));

      sessionService.autoCloseSession(1L);

      assertThat(activeSession.getStatus()).isEqualTo(SessionStatus.COMPLETED);
      verify(attendanceService).markAbsentForRemainingWaiting(1L);
    }

    @Test
    @DisplayName("단체의 자동 결석 처리 설정이 꺼져 있으면 세션은 종료하되 결석 처리는 건너뛴다")
    void closesSessionButSkipsAbsent_whenAutoAbsentDisabled() {
      Organization organization = Organization.builder().name("테스트 단체").code("CODE2").active(true).build();
      organization.updatePolicy(false, null, null, null);
      given(sessionRepository.findById(1L)).willReturn(Optional.of(activeSession));
      given(organizationRepository.findById(10L)).willReturn(Optional.of(organization));

      sessionService.autoCloseSession(1L);

      assertThat(activeSession.getStatus()).isEqualTo(SessionStatus.COMPLETED);
      verify(attendanceService, never()).markAbsentForRemainingWaiting(anyLong());
    }

    @Test
    @DisplayName("단체를 찾을 수 없으면 기본값(true)으로 간주해 결석 처리한다")
    void marksAbsent_whenOrganizationNotFound() {
      given(sessionRepository.findById(1L)).willReturn(Optional.of(activeSession));
      given(organizationRepository.findById(10L)).willReturn(Optional.empty());

      sessionService.autoCloseSession(1L);

      assertThat(activeSession.getStatus()).isEqualTo(SessionStatus.COMPLETED);
      verify(attendanceService).markAbsentForRemainingWaiting(1L);
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 EntityNotFoundException")
    void throwsWhenSessionNotFound() {
      given(sessionRepository.findById(99L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> sessionService.autoCloseSession(99L))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("findExpiredScheduledSessionIds()")
  class FindExpiredScheduledSessionIds {

    @Test
    @DisplayName("시작 없이 종료 시각이 지난 SCHEDULED 세션들의 ID 목록을 반환한다")
    void returnsIdsOfExpiredScheduledSessions() {
      given(sessionRepository.findByStatusAndEndTimeBefore(eq(SessionStatus.SCHEDULED), any(LocalDateTime.class)))
          .willReturn(List.of(scheduledSession));

      List<Long> result = sessionService.findExpiredScheduledSessionIds();

      assertThat(result).containsExactly(2L);
    }
  }

  @Nested
  @DisplayName("autoCancelSession()")
  class AutoCancelSession {

    @Test
    @DisplayName("시작 없이 종료 시각이 지난 세션을 취소 처리하고, 결석 처리는 호출하지 않는다")
    void cancelsScheduledSession() {
      given(sessionRepository.findById(2L)).willReturn(Optional.of(scheduledSession));

      sessionService.autoCancelSession(2L);

      assertThat(scheduledSession.getStatus()).isEqualTo(SessionStatus.CANCELED);
      verify(attendanceService, never()).markAbsentForRemainingWaiting(anyLong());
    }

    @Test
    @DisplayName("존재하지 않는 세션이면 EntityNotFoundException")
    void throwsWhenSessionNotFound() {
      given(sessionRepository.findById(99L)).willReturn(Optional.empty());

      assertThatThrownBy(() -> sessionService.autoCancelSession(99L))
          .isInstanceOf(EntityNotFoundException.class);
    }
  }
}
