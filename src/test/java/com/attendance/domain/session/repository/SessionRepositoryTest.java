package com.attendance.domain.session.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

/**
 * SessionRepository 커스텀 쿼리 테스트 @DataJpaTest로 인메모리 H2 DB에 실제 AttendanceSession을 저장하고, 특히
 * findActiveSessionsByNfcTagId()의 시간 범위 조건(startTime <= now <= endTime)과 정렬(startTime DESC)이 정확히
 * 동작하는지 검증한다. (동일 태그에 여러 ACTIVE 세션이 걸려 지각 판정이 틀어졌던 실제 버그의 회귀 테스트 성격)
 *
 * <p>검증하는 주요 정책: - 현재 시각이 세션 시간 범위 안일 때만 활성 세션으로 조회된다 (findActiveSessionsByNfcTagId) - 시작 전/종료 후
 * 세션은 ACTIVE 상태여도 조회되지 않는다 - 동일 태그에 여러 활성 세션이 걸리면 startTime 내림차순으로 정렬된다 (방어 로직 검증) - 태그+상태로 세션을
 * 필터링한다 (findByNfcTagIdAndStatus, 중복 ACTIVE 세션 방지에 사용) - 날짜/상태별 세션 수를 정확히 카운트한다
 * (countBySessionDate, countByStatus) - 그룹+상태로 세션을 필터링한다 (findByGroupNameAndStatus) - 완료된 세션을
 * 최신순으로, 지정한 개수만큼 조회한다 (findByStatusOrderBySessionDateDesc)
 */
@DataJpaTest
class SessionRepositoryTest {

  /**
   * @Autowired 실제 DB 대신 인메모리 H2로 자동 구성된 SessionRepository 빈을 주입받는다. @Autowired TestEntityManager로
   * 영속성 컨텍스트를 직접 다루어 테스트 데이터를 세팅/flush한다.
   */
  @Autowired private SessionRepository sessionRepository;

  @Autowired private TestEntityManager em;

  private NfcTag saveTag(String uid) {
    // 세션-태그 연관관계 테스트를 위한 활성 NFC 태그 생성
    NfcTag tag = NfcTag.builder().uid(uid).name("테스트 태그").status(NfcTagStatus.ACTIVE).build();
    return em.persistAndFlush(tag);
  }

  private AttendanceSession saveSession(
      NfcTag tag,
      String groupName,
      SessionStatus status,
      LocalDateTime startTime,
      LocalDateTime endTime) {
    // 테스트용 세션 생성 (title/startTime/endTime/status/createdBy는 NOT NULL)
    AttendanceSession session =
        AttendanceSession.builder()
            .organizationId(1L)
            .title("테스트 세션")
            .groupName(groupName)
            .sessionDate(startTime.toLocalDate())
            .startTime(startTime)
            .endTime(endTime)
            .lateThresholdMinutes(10)
            .status(status)
            .nfcTag(tag)
            .createdBy(1L)
            .build();
    return em.persistAndFlush(session);
  }

  @Nested
  @DisplayName("findActiveSessionsByNfcTagId()")
  class FindActiveSessionsByNfcTagId {

    @Test
    @DisplayName("현재 시각이 세션 시간 범위 안이면 조회된다")
    void withinTimeRange_returnsSession() {
      // given
      // 지금부터 10분 전에 시작해서 50분 뒤에 끝나는 ACTIVE 세션
      NfcTag tag = saveTag("TAG-001");
      saveSession(
          tag,
          "A반",
          SessionStatus.ACTIVE,
          LocalDateTime.now().minusMinutes(10),
          LocalDateTime.now().plusMinutes(50));

      // when
      List<AttendanceSession> result = sessionRepository.findActiveSessionsByNfcTagId(tag.getId());

      // then
      assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("이미 종료된(endTime이 과거인) 세션은 ACTIVE여도 조회되지 않는다")
    void endTimeInPast_returnsEmpty() {
      // given
      // 실제로 겪었던 버그 상황 재현: 종료 시각이 지났는데도 status가 ACTIVE로 남아있는 세션
      NfcTag tag = saveTag("TAG-001");
      saveSession(
          tag,
          "A반",
          SessionStatus.ACTIVE,
          LocalDateTime.now().minusDays(2),
          LocalDateTime.now().minusDays(2).plusMinutes(35));

      // when
      List<AttendanceSession> result = sessionRepository.findActiveSessionsByNfcTagId(tag.getId());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("아직 시작 전(startTime이 미래인) 세션은 조회되지 않는다")
    void startTimeInFuture_returnsEmpty() {
      // given
      // 10분 뒤에나 시작하는 ACTIVE 세션 (아직 체크인 대상 아님)
      NfcTag tag = saveTag("TAG-001");
      saveSession(
          tag,
          "A반",
          SessionStatus.ACTIVE,
          LocalDateTime.now().plusMinutes(10),
          LocalDateTime.now().plusMinutes(60));

      // when
      List<AttendanceSession> result = sessionRepository.findActiveSessionsByNfcTagId(tag.getId());

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("동일 태그에 여러 활성 세션이 걸려도 startTime 내림차순(최근 시작)으로 정렬된다")
    void multipleActiveSessions_orderedByStartTimeDesc() {
      // given
      // 같은 태그를 공유하는 두 ACTIVE 세션이 시간 범위상 동시에 걸리는 상황 (방어 로직 검증용)
      NfcTag tag = saveTag("TAG-001");
      AttendanceSession olderSession =
          saveSession(
              tag,
              "A반",
              SessionStatus.ACTIVE,
              LocalDateTime.now().minusMinutes(30),
              LocalDateTime.now().plusMinutes(30));
      AttendanceSession newerSession =
          saveSession(
              tag,
              "B반",
              SessionStatus.ACTIVE,
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusMinutes(30));

      // when
      List<AttendanceSession> result = sessionRepository.findActiveSessionsByNfcTagId(tag.getId());

      // then
      // 가장 최근에 시작된 세션(newerSession)이 첫 번째로 와야 함
      assertThat(result).hasSize(2);
      assertThat(result.get(0).getId()).isEqualTo(newerSession.getId());
      assertThat(result.get(1).getId()).isEqualTo(olderSession.getId());
    }
  }

  @Nested
  @DisplayName("findByNfcTagIdAndStatus()")
  class FindByNfcTagIdAndStatus {

    @Test
    @DisplayName("같은 태그라도 지정한 상태의 세션만 조회된다 (동일 태그 중복 ACTIVE 검증용)")
    void filtersOnlyMatchingStatus() {
      // given
      // 같은 태그에 ACTIVE 세션 1개, COMPLETED 세션 1개가 있는 상황
      NfcTag tag = saveTag("TAG-001");
      AttendanceSession activeSession =
          saveSession(
              tag,
              "A반",
              SessionStatus.ACTIVE,
              LocalDateTime.now().minusMinutes(5),
              LocalDateTime.now().plusMinutes(30));
      saveSession(
          tag,
          "B반",
          SessionStatus.COMPLETED,
          LocalDateTime.now().minusDays(1),
          LocalDateTime.now().minusDays(1).plusMinutes(30));

      // when
      List<AttendanceSession> result =
          sessionRepository.findByNfcTagIdAndStatus(tag.getId(), SessionStatus.ACTIVE);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getId()).isEqualTo(activeSession.getId());
    }
  }

  @Nested
  @DisplayName("countBySessionDate() / countByStatus()")
  class CountQueries {

    @Test
    @DisplayName("오늘 날짜의 세션 수를 정확히 센다")
    void countBySessionDate_countsOnlyMatchingDate() {
      // given
      // 오늘 세션 1개, 어제 세션 1개가 있는 상황
      NfcTag tag = saveTag("TAG-001");
      LocalDate today = LocalDate.now();
      saveSession(tag, "A반", SessionStatus.SCHEDULED, today.atTime(9, 0), today.atTime(10, 0));
      saveSession(
          tag,
          "B반",
          SessionStatus.SCHEDULED,
          today.minusDays(1).atTime(9, 0),
          today.minusDays(1).atTime(10, 0));

      // when & then
      assertThat(sessionRepository.countBySessionDate(today)).isEqualTo(1);
    }

    @Test
    @DisplayName("상태별 세션 수를 정확히 센다")
    void countByStatus_countsOnlyMatchingStatus() {
      // given
      // COMPLETED 2개, SCHEDULED 1개가 있는 상황
      NfcTag tag = saveTag("TAG-001");
      saveSession(
          tag,
          "A반",
          SessionStatus.COMPLETED,
          LocalDateTime.now().minusDays(1),
          LocalDateTime.now().minusDays(1).plusHours(1));
      saveSession(
          tag,
          "B반",
          SessionStatus.COMPLETED,
          LocalDateTime.now().minusDays(2),
          LocalDateTime.now().minusDays(2).plusHours(1));
      saveSession(
          tag,
          "C반",
          SessionStatus.SCHEDULED,
          LocalDateTime.now().plusDays(1),
          LocalDateTime.now().plusDays(1).plusHours(1));

      // when & then
      assertThat(sessionRepository.countByStatus(SessionStatus.COMPLETED)).isEqualTo(2);
      assertThat(sessionRepository.countByStatus(SessionStatus.SCHEDULED)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("findByGroupNameAndStatus()")
  class FindByGroupNameAndStatus {

    @Test
    @DisplayName("같은 그룹이라도 지정한 상태의 세션만 조회된다")
    void filtersOnlyMatchingGroupAndStatus() {
      // given
      // A반 COMPLETED 1개, A반 SCHEDULED 1개, B반 COMPLETED 1개가 있는 상황
      NfcTag tag = saveTag("TAG-001");
      saveSession(
          tag,
          "A반",
          SessionStatus.COMPLETED,
          LocalDateTime.now().minusDays(1),
          LocalDateTime.now().minusDays(1).plusHours(1));
      saveSession(
          tag,
          "A반",
          SessionStatus.SCHEDULED,
          LocalDateTime.now().plusDays(1),
          LocalDateTime.now().plusDays(1).plusHours(1));
      saveSession(
          tag,
          "B반",
          SessionStatus.COMPLETED,
          LocalDateTime.now().minusDays(1),
          LocalDateTime.now().minusDays(1).plusHours(1));

      // when
      List<AttendanceSession> result =
          sessionRepository.findByGroupNameAndStatus("A반", SessionStatus.COMPLETED);

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getGroupName()).isEqualTo("A반");
    }
  }

  @Nested
  @DisplayName("findByStatusOrderBySessionDateDesc()")
  class FindByStatusOrderBySessionDateDesc {

    @Test
    @DisplayName("완료된 세션을 최신 날짜순으로, 지정한 개수만큼만 가져온다")
    void returnsRecentSessionsInDescOrderLimitedByPageable() {
      // given
      // 완료된 세션 3개를 서로 다른 날짜로 생성
      NfcTag tag = saveTag("TAG-001");
      AttendanceSession oldest =
          saveSession(
              tag,
              "A반",
              SessionStatus.COMPLETED,
              LocalDateTime.now().minusDays(3),
              LocalDateTime.now().minusDays(3).plusHours(1));
      AttendanceSession middle =
          saveSession(
              tag,
              "A반",
              SessionStatus.COMPLETED,
              LocalDateTime.now().minusDays(2),
              LocalDateTime.now().minusDays(2).plusHours(1));
      AttendanceSession newest =
          saveSession(
              tag,
              "A반",
              SessionStatus.COMPLETED,
              LocalDateTime.now().minusDays(1),
              LocalDateTime.now().minusDays(1).plusHours(1));

      // when - 최근 2개만 요청
      List<AttendanceSession> result =
          sessionRepository.findByStatusOrderBySessionDateDesc(
              SessionStatus.COMPLETED, PageRequest.of(0, 2));

      // then
      // 최신순 2개(newest, middle)만 포함되고, 페이지 크기를 넘어간 oldest는 제외되어야 함
      assertThat(result).hasSize(2);
      assertThat(result.get(0).getId()).isEqualTo(newest.getId());
      assertThat(result.get(1).getId()).isEqualTo(middle.getId());
      assertThat(result).extracting(AttendanceSession::getId).doesNotContain(oldest.getId());
    }
  }
}
