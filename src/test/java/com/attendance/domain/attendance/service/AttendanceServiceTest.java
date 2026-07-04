package com.attendance.domain.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * AttendanceService 단위 테스트
 *
 * Spring Context나 실제 DB를 띄우지 않고,
 * Mockito로 Repository들을 대체하여 AttendanceService의 출석 처리 로직만 검증한다.
 *
 * 검증하는 주요 정책:
 * - 비활성 NFC 태그는 출석 처리 불가
 * - 활성 세션이 없으면 출석 처리 불가
 * - 이미 PRESENT 처리된 출석은 중복 체크인으로 차단
 * - WAITING 레코드가 있으면 신규 생성 없이 기존 레코드 갱신
 * - 기존 레코드가 없으면 새 출석 레코드 생성
 * - 지각 기준 초과 시 LATE 처리
 * - 세션 시작 전 그룹 학생에게 WAITING 레코드 사전 생성
 * - 남은 WAITING 레코드는 결석 처리
 */
@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    /**
     * @Mock 실제 DB를 사용하지 않고 Repository들을 Mock으로 대체한다.
     * @InjectMocks 선언한 Mock 객체들이 AttendanceService에 자동으로 주입된다.
     */
    @Mock private AttendanceRepository attendanceRepository;
    @Mock private SessionRepository sessionRepository;
    @Mock private NfcTagRepository nfcTagRepository;
    @Mock private UserRepository userRepository;
    @InjectMocks private AttendanceService attendanceService;

    private NfcTag activeTag(Long id) {
        // 테스트에서 반복적으로 사용할 활성 NFC 태그 생성
        NfcTag tag =
                NfcTag.builder()
                        .uid("TAG-001")
                        .name("테스트 태그")
                        .location("301호")
                        .status(NfcTagStatus.ACTIVE)
                        .build();

        // id는 @GeneratedValue라 테스트 객체에서 직접 세팅하지 않고,
        // 세션 조회는 Repository Mock의 반환값으로 제어한다.
        return tag;
    }

    private AttendanceSession activeSession(Long id, String groupName, LocalDateTime startTime) {
        // 테스트에서 반복적으로 사용할 활성 출석 세션 생성
        return AttendanceSession.builder()
                .id(id)
                .groupName(groupName)
                .startTime(startTime)
                .lateThresholdMinutes(10)
                .build();
    }

    @Nested
    @DisplayName("checkIn()")
    class CheckIn {

        @Test
        @DisplayName("비활성화된 NFC 태그면 INACTIVE_NFC_TAG 예외가 발생한다")
        void inactiveNfcTag_throwsException() {
            // given
            // 존재하는 태그이지만 상태가 INACTIVE인 상황
            NfcTag inactiveTag =
                    NfcTag.builder().uid("TAG-001").name("태그").status(NfcTagStatus.INACTIVE).build();
            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(inactiveTag));
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when & then
            // 비활성 태그로 체크인하면 INACTIVE_NFC_TAG 예외가 발생해야 한다
            assertThatThrownBy(() -> attendanceService.checkIn(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.INACTIVE_NFC_TAG);
        }

        @Test
        @DisplayName("태그에 연결된 활성 세션이 없으면 SESSION_NOT_ACTIVE 예외가 발생한다")
        void noActiveSession_throwsException() {
            // given
            // 태그는 활성 상태이지만 연결된 활성 세션이 없는 상황
            NfcTag tag = activeTag(1L);
            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(tag));
            given(sessionRepository.findActiveSessionsByNfcTagId(any())).willReturn(List.of());
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when & then
            // 현재 출석 가능한 세션이 없으면 SESSION_NOT_ACTIVE 예외가 발생해야 한다
            assertThatThrownBy(() -> attendanceService.checkIn(1L, request))
                    .isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode())
                    .isEqualTo(ErrorCode.SESSION_NOT_ACTIVE);
        }

        @Test
        @DisplayName("이미 PRESENT로 처리된 레코드가 있으면 DUPLICATE_ATTENDANCE 예외가 발생한다")
        void alreadyPresentRecord_throwsDuplicateException() {
            // given
            // 같은 사용자와 세션에 대해 이미 PRESENT 처리된 출석 레코드가 있는 상황
            NfcTag tag = activeTag(1L);
            AttendanceSession session = activeSession(10L, "A반", LocalDateTime.now().minusMinutes(5));
            AttendanceRecord existingPresentRecord =
                    AttendanceRecord.builder()
                            .userId(1L)
                            .sessionId(10L)
                            .status(AttendanceStatus.PRESENT)
                            .checkInTime(LocalDateTime.now().minusMinutes(1))
                            .build();

            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(tag));
            given(sessionRepository.findActiveSessionsByNfcTagId(any())).willReturn(List.of(session));
            given(attendanceRepository.findByUserIdAndSessionId(1L, 10L))
                    .willReturn(Optional.of(existingPresentRecord));
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when & then
            // 이미 출석 처리된 사용자가 다시 체크인하면 중복 출석 예외가 발생해야 한다
            assertThatThrownBy(() -> attendanceService.checkIn(1L, request))
                    .isInstanceOf(DuplicateException.class)
                    .extracting(e -> ((DuplicateException) e).getErrorCode())
                    .isEqualTo(ErrorCode.DUPLICATE_ATTENDANCE);

            // 중복이 확정되면 신규 출석 레코드가 저장되면 안 됨
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("사전 생성된 WAITING 레코드가 있으면 새로 저장하지 않고 기존 레코드를 갱신한다")
        void existingWaitingRecord_updatesInPlaceWithoutNewSave() {
            // given
            // 세션 시작 전에 만들어진 WAITING 레코드가 존재하는 상황
            NfcTag tag = activeTag(1L);
            AttendanceSession session = activeSession(10L, "A반", LocalDateTime.now().minusMinutes(5));
            AttendanceRecord waitingRecord =
                    AttendanceRecord.builder().userId(1L).sessionId(10L).status(AttendanceStatus.WAITING).build();
            User user = User.builder().id(1L).name("홍길동").role(UserRole.STUDENT).build();

            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(tag));
            given(sessionRepository.findActiveSessionsByNfcTagId(any())).willReturn(List.of(session));
            given(attendanceRepository.findByUserIdAndSessionId(1L, 10L))
                    .willReturn(Optional.of(waitingRecord));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when
            AttendanceResponse response = attendanceService.checkIn(1L, request);

            // then
            // 기존 WAITING 레코드가 PRESENT로 갱신되고, 신규 save는 호출되지 않아야 함
            assertThat(waitingRecord.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            assertThat(response.getStatus()).isEqualTo(AttendanceStatus.PRESENT);
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("기존 레코드가 없으면(그룹 미지정 세션 등) 새 레코드를 생성한다")
        void noExistingRecord_createsNewRecord() {
            // given
            // 사전 생성된 WAITING 레코드가 없어서 체크인 시 새 레코드를 만들어야 하는 상황
            NfcTag tag = activeTag(1L);
            AttendanceSession session = activeSession(10L, null, LocalDateTime.now().minusMinutes(5));
            User user = User.builder().id(1L).name("홍길동").role(UserRole.STUDENT).build();
            AttendanceRecord savedRecord =
                    AttendanceRecord.builder()
                            .userId(1L)
                            .sessionId(10L)
                            .status(AttendanceStatus.PRESENT)
                            .checkInTime(LocalDateTime.now())
                            .build();

            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(tag));
            given(sessionRepository.findActiveSessionsByNfcTagId(any())).willReturn(List.of(session));
            given(attendanceRepository.findByUserIdAndSessionId(1L, 10L)).willReturn(Optional.empty());
            given(attendanceRepository.save(any(AttendanceRecord.class))).willReturn(savedRecord);
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when
            attendanceService.checkIn(1L, request);

            // then
            // 기존 레코드가 없으면 신규 출석 레코드가 1번 저장되어야 함
            verify(attendanceRepository, times(1)).save(any(AttendanceRecord.class));
        }

        @Test
        @DisplayName("세션 시작시각 + 지각기준(분)을 넘겨서 체크인하면 LATE로 판정된다")
        void lateArrival_marksLateStatus() {
            // given
            // startTime을 20분 전으로 설정하고 lateThreshold를 10분으로 두어 지각 기준을 초과한 상황
            NfcTag tag = activeTag(1L);
            AttendanceSession session = activeSession(10L, null, LocalDateTime.now().minusMinutes(20));
            User user = User.builder().id(1L).name("홍길동").role(UserRole.STUDENT).build();

            given(nfcTagRepository.findByUid("TAG-001")).willReturn(Optional.of(tag));
            given(sessionRepository.findActiveSessionsByNfcTagId(any())).willReturn(List.of(session));
            given(attendanceRepository.findByUserIdAndSessionId(1L, 10L)).willReturn(Optional.empty());
            given(attendanceRepository.save(any(AttendanceRecord.class)))
                    .willAnswer(invocation -> invocation.getArgument(0));
            given(userRepository.findById(1L)).willReturn(Optional.of(user));
            CheckInRequest request = new CheckInRequest("TAG-001");

            // when
            AttendanceResponse response = attendanceService.checkIn(1L, request);

            // then
            // 지각 기준을 넘겨 체크인하면 출석 상태가 LATE여야 함
            assertThat(response.getStatus()).isEqualTo(AttendanceStatus.LATE);
        }
    }

    @Nested
    @DisplayName("initializeWaitingRecords()")
    class InitializeWaitingRecords {

        @Test
        @DisplayName("그룹이 지정되지 않은 세션은 아무 것도 하지 않는다")
        void groupNameNull_doesNothing() {
            // given
            // groupName이 없는 세션은 특정 학생 그룹을 대상으로 WAITING 레코드를 만들 수 없는 상황
            AttendanceSession session = activeSession(10L, null, LocalDateTime.now());

            // when
            attendanceService.initializeWaitingRecords(session);

            // then
            // 대상 학생 조회나 WAITING 레코드 저장이 일어나면 안 됨
            verify(userRepository, never()).findByRoleAndGroupName(any(), any());
            verify(attendanceRepository, never()).save(any());
        }

        @Test
        @DisplayName("그룹 학생 전원에게 WAITING 레코드를 생성하되, 이미 레코드가 있는 학생은 건너뛴다")
        void createsWaitingRecordsForTargetsExceptExisting() {
            // given
            // A반 학생 2명 중 student1은 레코드가 없고, student2는 이미 레코드가 있는 상황
            AttendanceSession session = activeSession(10L, "A반", LocalDateTime.now());
            User student1 = User.builder().id(1L).role(UserRole.STUDENT).groupName("A반").build();
            User student2 = User.builder().id(2L).role(UserRole.STUDENT).groupName("A반").build();

            given(userRepository.findByRoleAndGroupName(UserRole.STUDENT, "A반"))
                    .willReturn(List.of(student1, student2));
            given(attendanceRepository.existsByUserIdAndSessionId(1L, 10L)).willReturn(false);
            given(attendanceRepository.existsByUserIdAndSessionId(2L, 10L)).willReturn(true);

            // when
            attendanceService.initializeWaitingRecords(session);

            // then
            // 레코드가 없는 student1만 WAITING 레코드가 생성되고, student2는 건너뛰어야 함
            verify(attendanceRepository, times(1)).save(any(AttendanceRecord.class));
        }
    }

    @Nested
    @DisplayName("markAbsentForRemainingWaiting()")
    class MarkAbsentForRemainingWaiting {

        @Test
        @DisplayName("남은 WAITING 레코드를 전부 ABSENT로 변경한다")
        void updatesAllWaitingRecordsToAbsent() {
            // given
            // 세션 종료 후에도 체크인하지 않아 WAITING 상태로 남아 있는 레코드들이 존재하는 상황
            AttendanceRecord waiting1 =
                    AttendanceRecord.builder().userId(1L).sessionId(10L).status(AttendanceStatus.WAITING).build();
            AttendanceRecord waiting2 =
                    AttendanceRecord.builder().userId(2L).sessionId(10L).status(AttendanceStatus.WAITING).build();
            given(attendanceRepository.findBySessionIdAndStatus(10L, AttendanceStatus.WAITING))
                    .willReturn(List.of(waiting1, waiting2));

            // when
            attendanceService.markAbsentForRemainingWaiting(10L);

            // then
            // 남은 WAITING 레코드는 모두 ABSENT로 변경되고, 시스템 처리 기록이 남아야 함
            assertThat(waiting1.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(waiting2.getStatus()).isEqualTo(AttendanceStatus.ABSENT);
            assertThat(waiting1.getModifiedBy()).isEqualTo("SYSTEM");
        }
    }
}