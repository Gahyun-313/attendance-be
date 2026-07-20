package com.attendance.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

/**
 * AttendanceRepository 쿼리 메서드 테스트 @DataJpaTest로 인메모리 H2 DB에 실제 AttendanceRecord를 저장하고,
 * checkIn()/markAbsentForRemainingWaiting()이 의존하는 조회 메서드들이 정확한 결과를 주는지 검증한다.
 *
 * <p>검증하는 주요 정책: - user+session 조합으로 기존 레코드를 정확히 찾는다 (findByUserIdAndSessionId) - 세션+상태 조합으로 필터링한다
 * (findBySessionIdAndStatus) - user+session 레코드 존재 여부를 정확히 판별한다 (existsByUserIdAndSessionId) -
 * 전체/사용자별 상태 건수를 정확히 카운트한다 (countByStatus, countByUserIdAndStatus)
 */
@DataJpaTest
class AttendanceRepositoryTest {

  /**
   * @Autowired 실제 DB 대신 인메모리 H2로 자동 구성된 AttendanceRepository 빈을 주입받는다. @Autowired
   * TestEntityManager로 영속성 컨텍스트를 직접 다루어 테스트 데이터를 세팅/flush한다.
   */
  @Autowired private AttendanceRepository attendanceRepository;

  @Autowired private TestEntityManager em;

  private AttendanceRecord saveRecord(Long userId, Long sessionId, AttendanceStatus status) {
    // 테스트용 출석 레코드 생성 (userId/sessionId/status는 NOT NULL)
    AttendanceRecord attendanceRecord =
        AttendanceRecord.builder().userId(userId).sessionId(sessionId).status(status).build();
    return em.persistAndFlush(attendanceRecord);
  }

  @Nested
  @DisplayName("findByUserIdAndSessionId()")
  class FindByUserIdAndSessionId {

    @Test
    @DisplayName("해당 사용자+세션의 레코드가 있으면 조회된다")
    void existingRecord_returnsRecord() {
      // given
      // user 1, session 10에 WAITING 레코드가 이미 있는 상황
      saveRecord(1L, 10L, AttendanceStatus.WAITING);

      // when
      Optional<AttendanceRecord> result = attendanceRepository.findByUserIdAndSessionId(1L, 10L);

      // then
      assertThat(result).isPresent();
      assertThat(result.get().getStatus()).isEqualTo(AttendanceStatus.WAITING);
    }

    @Test
    @DisplayName("해당 사용자+세션의 레코드가 없으면 빈 값이 반환된다")
    void noRecord_returnsEmpty() {
      // given
      // 아무 레코드도 저장하지 않은 상황

      // when
      Optional<AttendanceRecord> result = attendanceRepository.findByUserIdAndSessionId(999L, 999L);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("findBySessionIdAndStatus()")
  class FindBySessionIdAndStatus {

    @Test
    @DisplayName("같은 세션이라도 지정한 상태의 레코드만 조회된다")
    void filtersOnlyMatchingStatus() {
      // given
      // 세션 10에 WAITING 2건, PRESENT 1건이 섞여 있는 상황
      saveRecord(1L, 10L, AttendanceStatus.WAITING);
      saveRecord(2L, 10L, AttendanceStatus.WAITING);
      saveRecord(3L, 10L, AttendanceStatus.PRESENT);

      // when
      List<AttendanceRecord> waitingRecords =
          attendanceRepository.findBySessionIdAndStatus(10L, AttendanceStatus.WAITING);

      // then
      assertThat(waitingRecords).hasSize(2);
      assertThat(waitingRecords)
          .extracting(AttendanceRecord::getUserId)
          .containsExactlyInAnyOrder(1L, 2L);
    }
  }

  @Nested
  @DisplayName("existsByUserIdAndSessionId()")
  class ExistsByUserIdAndSessionId {

    @Test
    @DisplayName("레코드가 있으면 true, 없으면 false를 반환한다")
    void returnsExistenceCorrectly() {
      // given
      // user 1, session 10 조합만 저장된 상황
      saveRecord(1L, 10L, AttendanceStatus.WAITING);

      // when & then
      assertThat(attendanceRepository.existsByUserIdAndSessionId(1L, 10L)).isTrue();
      assertThat(attendanceRepository.existsByUserIdAndSessionId(2L, 10L)).isFalse();
    }
  }

  @Nested
  @DisplayName("countByStatus() / countByUserIdAndStatus()")
  class CountQueries {

    @Test
    @DisplayName("전체 상태별 건수를 정확히 센다")
    void countByStatus_countsAllMatchingRecords() {
      // given
      // PRESENT 2건, ABSENT 1건이 섞여 있는 상황
      saveRecord(1L, 10L, AttendanceStatus.PRESENT);
      saveRecord(2L, 10L, AttendanceStatus.PRESENT);
      saveRecord(3L, 10L, AttendanceStatus.ABSENT);

      // when & then
      assertThat(attendanceRepository.countByStatus(AttendanceStatus.PRESENT)).isEqualTo(2);
      assertThat(attendanceRepository.countByStatus(AttendanceStatus.ABSENT)).isEqualTo(1);
    }

    @Test
    @DisplayName("특정 사용자의 상태별 건수를 정확히 센다")
    void countByUserIdAndStatus_countsOnlyThatUser() {
      // given
      // user 1은 세션 두 개에서 각각 PRESENT, LATE / user 2는 PRESENT 한 건
      saveRecord(1L, 10L, AttendanceStatus.PRESENT);
      saveRecord(1L, 11L, AttendanceStatus.LATE);
      saveRecord(2L, 10L, AttendanceStatus.PRESENT);

      // when & then
      assertThat(attendanceRepository.countByUserIdAndStatus(1L, AttendanceStatus.PRESENT))
          .isEqualTo(1);
      assertThat(attendanceRepository.countByUserId(1L)).isEqualTo(2);
    }
  }
}
