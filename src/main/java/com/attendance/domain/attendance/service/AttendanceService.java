package com.attendance.domain.attendance.service;

// attendance
import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.AttendanceStatusUpdateRequest;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.dto.RecentAttendanceResponse;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.event.AttendanceCheckedInEvent;
import com.attendance.domain.attendance.repository.AttendanceRepository;
// nfc
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
// organization
import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
// session
import com.attendance.domain.session.SessionStatus;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
// user
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
// global
import com.attendance.global.config.RedisConfig;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 출석 기록 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

  // 체크인 분산 락 설정값. 근거는 findOrCreateRecordWithLock() 참고.
  private static final String LOCK_KEY_PREFIX = "lock:checkin:";
  private static final long LOCK_WAIT_SECONDS = 3L;
  private static final int RECENT_MIN_LIMIT = 1;
  private static final int RECENT_MAX_LIMIT = 50;

  private final AttendanceRepository attendanceRepository;
  private final SessionRepository sessionRepository;
  private final NfcTagRepository nfcTagRepository;
  private final UserRepository userRepository;
  private final OrganizationRepository organizationRepository;

  private final ApplicationEventPublisher eventPublisher; // 체크인 완료 후 실시간 푸시 트리거용
  private final CacheManager cacheManager; // 세션 대시보드 캐시(sessionDashboard) 수동 무효화용

  // 동시 체크인 경합을 막는 분산 락. redisson-spring-boot-starter가 application-local.yml의
  // redis 설정을 읽어 RedissonClient 빈을 자동 생성해주므로 별도 Config 클래스가 필요 없다.
  private final RedissonClient redissonClient;

  /**
   * 출석 체크인 처리(STUDENT) NFC UID를 검증하고 활성 세션을 역추적해 지각 여부를 판정한 뒤, 분산 락 안에서 출석 레코드를 갱신/생성 WAITING 레코드가
   * 있으면 갱신하고 없으면 새로 생성하며, 이미 처리된 레코드가 있으면 중복 출석 예외를 던진다.
   *
   * <p>격리 수준을 READ_COMMITTED로 낮춘 이유: 기본 REPEATABLE READ는 락을 잡기 전인 1단계에서 이미 스냅샷이 고정돼서, 동시 요청이 락을
   * 순서대로 통과해도 앞선 요청의 커밋을 못 보고 중복 INSERT를 시도할 수 있다. READ_COMMITTED는 쿼리를 실행하는 시점마다 최신 커밋을 보므로 이 문제를
   * 막는다.
   */
  @Transactional(isolation = Isolation.READ_COMMITTED)
  public AttendanceResponse checkIn(Long userId, CheckInRequest request) {
    // 1. NFC 태그 조회 및 활성 상태 확인
    NfcTag nfcTag =
        nfcTagRepository
            .findByUid(request.getNfcTagUid())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
    if (!nfcTag.isActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_NFC_TAG);
    }

    // 2. 태그로 활성 세션 역추적. 요청에는 sessionId가 없고, 여러 개가 잡히면 첫 번째를 사용한다.
    AttendanceSession session =
        sessionRepository.findActiveSessionsByNfcTagId(nfcTag.getId()).stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_ACTIVE));

    // 2-1. NFC 태그 위치 검증. 단체 설정이 켜져 있고 location이 다르면 체크인을 차단한다.
    validateNfcLocationIfEnabled(session, nfcTag);

    // 3. session.isLate() 기준으로 출석/지각 판정
    LocalDateTime checkInTime = LocalDateTime.now();
    AttendanceStatus status =
        session.isLate(checkInTime) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

    // 4. 분산 락으로 감싼 임계 구역이다. 동시 체크인 시 중복 INSERT를 막기 위함이며,
    //    락 점유 시간을 줄이려고 앞단(1~3단계)은 락 밖에 둔다.
    AttendanceRecord attendanceRecord =
        findOrCreateRecordWithLock(userId, session, status, checkInTime, nfcTag);

    // 4-1. 대시보드 캐시 무효화. TTL(5초)이 끝나길 기다리지 않고 즉시 반영한다.
    evictSessionDashboard(session.getId());

    // 5. 부가 정보 갱신. 같은 트랜잭션 내 managed 엔티티라 더티 체킹으로 반영된다.
    nfcTag.markUsed(); // 태그 마지막 사용시각 갱신
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    user.recordFirstAttendanceIfAbsent(checkInTime); // 사용자 첫 출석 시각 기록

    // 6. 실시간 푸시 이벤트 발행. 아직 커밋 전이라 값 대신 PK만 전달하고,
    //    실제 조회/전송은 AttendanceEventListener가 커밋 후(AFTER_COMMIT)에 처리한다.
    eventPublisher.publishEvent(
        new AttendanceCheckedInEvent(
            session.getId(), attendanceRecord.getId(), session.getOrganizationId()));

    return AttendanceResponse.from(attendanceRecord, user);
  }

  /** NFC 태그 위치 검증. 단체 설정이 켜져 있을 때만 세션/태그 location 일치를 확인하고, 하나라도 비어 있으면 통과시킨다. */
  private void validateNfcLocationIfEnabled(AttendanceSession session, NfcTag nfcTag) {
    boolean enabled =
        organizationRepository
            .findById(session.getOrganizationId())
            .map(Organization::getNfcLocationValidationEnabled)
            .orElse(false);
    if (!enabled) {
      return;
    }
    String sessionLocation = session.getLocation();
    String tagLocation = nfcTag.getLocation();
    if (sessionLocation == null || tagLocation == null) {
      return;
    }
    if (!sessionLocation.equals(tagLocation)) {
      throw new BusinessException(ErrorCode.LOCATION_MISMATCH);
    }
  }

  /** checkIn()의 레코드 조회→저장 구간을 사용자+세션 단위 분산 락으로 감싸는 헬퍼. 중복 클릭/재시도로 인한 경합을 막는다. */
  // SonarLint(java:S2222) 오탐 억제: unlock()이 releaseLockAfterTransaction()에서 항상
  // 예약(트랜잭션 있음)되거나 즉시 실행(없음)되므로 실제로는 모든 경로에서 안전하게 풀린다.
  @SuppressWarnings("java:S2222")
  private AttendanceRecord findOrCreateRecordWithLock(
      Long userId,
      AttendanceSession session,
      AttendanceStatus status,
      LocalDateTime checkInTime,
      NfcTag nfcTag) {
    RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId + ":" + session.getId());
    boolean acquired;
    try {
      // waitTime을 3초로 둬서, 락을 못 얻으면 바로 포기시키고 재시도를 유도한다.
      // leaseTime을 지정하지 않으면 워치독이 활성화된다. 락은 트랜잭션 커밋 후에야 풀려 점유
      // 시간이 가변적인데, 고정 leaseTime을 쓰면 처리 중에 만료될 수 있다. 워치독이 점유 동안
      // 만료 시간을 자동 연장해주고, 서버가 죽어 연장이 멈춰도 기본 30초 뒤 자동 해제된다.
      acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
    }
    if (!acquired) {
      // 락 획득 실패는 같은 사용자의 같은 세션 체크인이 이미 처리 중이라는 뜻이다. DB까지 가지 않고 바로 409를 응답한다.
      throw new BusinessException(ErrorCode.CHECKIN_IN_PROGRESS);
    }

    // 락 해제는 트랜잭션 종료 시점으로 지연(이유는 releaseLockAfterTransaction() 참고).
    releaseLockAfterTransaction(lock);

    // 기존 WAITING 레코드가 있으면 체크인 정보로 갱신, 없으면 신규 생성
    return attendanceRepository
        .findByUserIdAndSessionId(userId, session.getId())
        .map(
            existing -> {
              if (existing.getStatus() != AttendanceStatus.WAITING) {
                throw new DuplicateException(ErrorCode.DUPLICATE_ATTENDANCE);
              }
              existing.checkIn(status, checkInTime, nfcTag.getUid(), nfcTag.getLocation());
              return existing;
            })
        .orElseGet(
            () ->
                attendanceRepository.save(
                    AttendanceRecord.builder()
                        .userId(userId)
                        .sessionId(session.getId())
                        .status(status)
                        .checkInTime(checkInTime)
                        .nfcTagUid(nfcTag.getUid())
                        .nfcLocation(nfcTag.getLocation())
                        .build()));
  }

  /**
   * 락 해제를 메서드 종료가 아니라 이 메서드를 호출한 트랜잭션이 실제로 커밋되는 시점으로 미룬다.
   *
   * <p>바로 unlock()하면 아직 커밋 전인 나머지 단계(캐시 무효화, 이벤트 발행 등) 사이에 다른 요청이 락을 잡고 "아직 커밋 안 됨"을 보고 중복 INSERT를
   * 시도할 수 있다. afterCompletion 콜백으로 해제를 미루면 다음 요청은 이전 트랜잭션이 커밋된 뒤에만 락을 잡게 되어 이 문제가 사라진다.
   */
  private void releaseLockAfterTransaction(RLock lock) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
              if (lock.isHeldByCurrentThread()) {
                lock.unlock();
              }
            }
          });
    } else {
      // 트랜잭션 없이 직접 호출된 경우(예: 단위 테스트)에는 기다릴 커밋이 없으므로 바로 해제한다.
      if (lock.isHeldByCurrentThread()) {
        lock.unlock();
      }
    }
  }

  /** 내 출석 기록 조회(STUDENT, 페이징) */
  public Page<AttendanceResponse> getMyAttendances(Long userId, Pageable pageable) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    // 본인 기록만 조회하므로 User는 1회만 조회해 재사용한다.
    return attendanceRepository
        .findByUserId(userId, pageable)
        .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, user));
  }

  /** 세션별 출석 현황 조회(ADMIN) */
  public List<AttendanceResponse> getSessionAttendances(Long sessionId, Long organizationId) {
    verifySessionOrganization(sessionId, organizationId, ErrorCode.SESSION_NOT_FOUND);
    List<AttendanceRecord> records = attendanceRepository.findBySessionId(sessionId);

    // 레코드마다 User를 반복 조회하면 N+1이 발생하므로, userId를 모아 한 번에 조회한 뒤 Map으로 매핑한다.
    List<Long> userIds = records.stream().map(AttendanceRecord::getUserId).distinct().toList();
    Map<Long, User> userMap =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, user -> user));

    // 레코드와 사용자 정보를 조합해 응답 DTO로 변환
    return records.stream()
        .map(
            attendanceRecord ->
                AttendanceResponse.from(
                    attendanceRecord, userMap.get(attendanceRecord.getUserId())))
        .toList();
  }

  /** 출석 상태 수동 수정(ADMIN). modifiedBy는 인증된 관리자 이름이며, 다른 단체 소속이면 404로 존재 자체를 숨긴다. */
  @Transactional
  public AttendanceResponse updateStatus(
      Long attendanceId,
      AttendanceStatusUpdateRequest request,
      String modifiedBy,
      Long organizationId) {
    AttendanceRecord attendanceRecord =
        attendanceRepository
            .findById(attendanceId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
    verifySessionOrganization(
        attendanceRecord.getSessionId(), organizationId, ErrorCode.ATTENDANCE_NOT_FOUND);

    // 도메인 메서드로 상태 변경. 누가/왜 바꿨는지도 함께 기록한다.
    attendanceRecord.modifyStatus(request.getStatus(), modifiedBy, request.getModifyReason());

    // 상태 변경 직후 대시보드에 옛날 숫자가 보이지 않도록 캐시를 즉시 비운다(checkIn()과 동일).
    evictSessionDashboard(attendanceRecord.getSessionId());

    User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
    return AttendanceResponse.from(attendanceRecord, user);
  }

  /** 출석 기록 삭제(ADMIN). 다른 단체 소속이면 404로 존재 자체를 숨긴다. */
  @Transactional
  public void deleteAttendance(Long attendanceId, Long organizationId) {
    AttendanceRecord attendanceRecord =
        attendanceRepository
            .findById(attendanceId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
    verifySessionOrganization(
        attendanceRecord.getSessionId(), organizationId, ErrorCode.ATTENDANCE_NOT_FOUND);
    attendanceRepository.deleteById(attendanceId);
    evictSessionDashboard(attendanceRecord.getSessionId());
  }

  /**
   * 세션별 출석 대시보드 조회(ADMIN) 상태별 레코드 수와 대상자 수(targetCount)를 집계하며, Redis에 5초 TTL로 캐싱하고 조작 시점마다 수동으로도
   * 무효화한다. 다른 단체 세션이면 404로 존재 자체를 숨긴다.
   */
  // key="#sessionId"로 고정한 이유: organizationId까지 기본 키에 포함되면 evictSessionDashboard(sessionId)의
  // 단일 키 evict가 캐시를 찾지 못한다. sessionId는 세션당 유일해 이것만으로 충분하다.
  @Cacheable(cacheNames = RedisConfig.CACHE_SESSION_DASHBOARD, key = "#sessionId")
  public AttendanceDashboardResponse getSessionDashboard(Long sessionId, Long organizationId) {
    // 세션 조회 및 소속 단체 일치 확인
    AttendanceSession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND));
    if (!session.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
    }

    // 상태별 출석 레코드 수 집계
    long total = attendanceRepository.countBySessionId(sessionId);
    long present =
        attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.PRESENT);
    long late = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.LATE);
    long absent =
        attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.ABSENT);
    long waiting =
        attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);

    // 대상자 수 산출. 세션에 그룹이 지정돼 있으면 해당 그룹 학생 수를 쓰고, 없으면 0으로 둔다.
    long targetCount =
        session.getGroupName() != null
            ? userRepository.countByRoleAndGroupName(UserRole.STUDENT, session.getGroupName())
            : 0L;

    return AttendanceDashboardResponse.of(
        sessionId, targetCount, total, present, late, absent, waiting);
  }

  /** 출석 레코드 단건 조회. AttendanceEventListener가 커밋 후 최신 상태로 푸시 페이로드를 만들 때 사용한다. */
  public AttendanceResponse getAttendanceRecord(Long attendanceId) {
    AttendanceRecord attendanceRecord =
        attendanceRepository
            .findById(attendanceId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
    User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
    return AttendanceResponse.from(attendanceRecord, user);
  }

  /**
   * 최근 체크인 기록 N건 조회(ADMIN, 세션 구분 없음). 사용자/세션명을 레코드마다 반복 조회하면 N+1 쿼리가 발생하므로 필요한 사용자와 세션을 각각 한 번에 조회한
   * 뒤 Map으로 연결한다.
   */
  public List<RecentAttendanceResponse> getRecentAttendances(Long organizationId, int limit) {
    int safeLimit = Math.max(RECENT_MIN_LIMIT, Math.min(limit, RECENT_MAX_LIMIT));

    // 단체 내 최근 출석 활동 조회 (실제 체크인 + 관리자 수동 수정/시스템 자동 결석 처리 포함)
    List<AttendanceRecord> records =
        attendanceRepository.findRecentAttendanceActivityByOrganizationId(
            organizationId, PageRequest.of(0, safeLimit));

    // 사용자/세션을 각각 한 번에 조회한 뒤 Map으로 연결
    Set<Long> userIds =
        records.stream().map(AttendanceRecord::getUserId).collect(Collectors.toSet());
    Map<Long, User> userMap =
        userRepository.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, Function.identity()));

    Set<Long> sessionIds =
        records.stream().map(AttendanceRecord::getSessionId).collect(Collectors.toSet());
    Map<Long, AttendanceSession> sessionMap =
        sessionRepository.findAllById(sessionIds).stream()
            .collect(Collectors.toMap(AttendanceSession::getId, Function.identity()));

    // 응답 DTO 변환
    return records.stream()
        .map(
            record ->
                RecentAttendanceResponse.of(
                    record, userMap.get(record.getUserId()), sessionMap.get(record.getSessionId())))
        .toList();
  }

  /** 세션 시작 시 대상 그룹 학생 전원에게 WAITING 레코드를 미리 생성한다. 그룹 미지정이면 스킵하고, 중복 호출돼도 안전하다. */
  @Transactional
  public void initializeWaitingRecords(AttendanceSession session) {
    if (session.getGroupName() == null) {
      return;
    }
    // 대상 그룹 학생 목록 조회
    List<User> targets =
        userRepository.findByRoleAndGroupName(UserRole.STUDENT, session.getGroupName());
    // 아직 레코드가 없는 학생에게만 WAITING 레코드 생성
    for (User target : targets) {
      if (!attendanceRepository.existsByUserIdAndSessionId(target.getId(), session.getId())) {
        attendanceRepository.save(
            AttendanceRecord.builder()
                .userId(target.getId())
                .sessionId(session.getId())
                .status(AttendanceStatus.WAITING)
                .build());
      }
    }
  }

  /** 세션 종료 시 아직 체크인하지 않은(WAITING) 레코드를 결석(ABSENT)으로 일괄 처리한다. */
  @Transactional
  public void markAbsentForRemainingWaiting(Long sessionId) {
    List<AttendanceRecord> waitingRecords =
        attendanceRepository.findBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);
    for (AttendanceRecord attendanceRecord : waitingRecords) {
      attendanceRecord.modifyStatus(AttendanceStatus.ABSENT, "SYSTEM", "세션 종료 시 자동 결석 처리");
    }
    // 대시보드 숫자가 크게 바뀌는 시점이므로 캐시를 즉시 비운다.
    evictSessionDashboard(sessionId);
  }

  /** 세션 종료 후 그룹에 새로 배정된 학생을 위해, 해당 그룹의 이미 COMPLETED된 세션에 결석 레코드를 소급 생성한다. */
  @Transactional
  public void backfillAbsentForCompletedSessions(
      Long userId, String groupName, Long organizationId) {
    if (groupName == null) {
      return;
    }
    List<AttendanceSession> completedSessions =
        sessionRepository.findByOrganizationIdAndGroupNameAndStatus(
            organizationId, groupName, SessionStatus.COMPLETED);
    for (AttendanceSession session : completedSessions) {
      if (!attendanceRepository.existsByUserIdAndSessionId(userId, session.getId())) {
        attendanceRepository.save(
            AttendanceRecord.builder()
                .userId(userId)
                .sessionId(session.getId())
                .status(AttendanceStatus.ABSENT)
                .modifiedBy("SYSTEM")
                .modifyReason("세션 종료 후 그룹 배정 - 자동 결석 처리")
                .build());
      }
    }
  }

  /** 세션 대시보드 캐시를 안전하게 비운다. getCache()가 @Nullable을 반환하므로 null 체크를 거친다. */
  private void evictSessionDashboard(Long sessionId) {
    Cache cache = cacheManager.getCache(RedisConfig.CACHE_SESSION_DASHBOARD);
    if (cache != null) {
      cache.evict(sessionId);
    }
  }

  /** 세션이 해당 단체 소속인지 검증한다. 불일치/미존재면 호출부 맥락에 맞는 404로 존재 자체를 숨긴다. */
  private void verifySessionOrganization(
      Long sessionId, Long organizationId, ErrorCode notFoundCode) {
    AttendanceSession session =
        sessionRepository
            .findById(sessionId)
            .orElseThrow(() -> new EntityNotFoundException(notFoundCode));
    if (!session.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(notFoundCode);
    }
  }
}
