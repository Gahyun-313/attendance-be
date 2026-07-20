package com.attendance.domain.attendance.service;

import com.attendance.domain.attendance.dto.AttendanceDashboardResponse;
import com.attendance.domain.attendance.dto.AttendanceResponse;
import com.attendance.domain.attendance.dto.AttendanceStatusUpdateRequest;
import com.attendance.domain.attendance.dto.CheckInRequest;
import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.event.AttendanceCheckedInEvent;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.repository.NfcTagRepository;
import com.attendance.domain.session.entity.AttendanceSession;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedisConfig;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 출석 기록 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    // 체크인 분산 락 설정값 - 값 근거는 findOrCreateRecordWithLock() 주석 참고
    private static final String LOCK_KEY_PREFIX = "lock:checkin:";
    private static final long LOCK_WAIT_SECONDS = 3L;

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final NfcTagRepository nfcTagRepository;
    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher; // 체크인 완료 후 실시간 푸시 트리거용
    private final CacheManager cacheManager; // 세션 대시보드 캐시(sessionDashboard)를 수동으로 비우는 데 사용
    // 동시 체크인 경합 방지용 분산 락. RedisConfig처럼 별도 Config 클래스가 없는 이유:
    // redisson-spring-boot-starter는 의존성만 추가하면 application-local.yml의 spring.data.redis.host/port를
    // 그대로 읽어서 RedissonClient 빈을 자동으로 만들어준다 - 직접 @Bean으로 만들 필요가 없다.
    private final RedissonClient redissonClient;

    /**
     * 출석 체크인 (STUDENT)
     * 흐름: NFC UID 검증 → 활성 세션 역추적 → 지각 판정 → 레코드 갱신/생성
     *      + 태그 사용시각/사용자 첫 출석시각 갱신
     * - 세션 시작 시 사전 생성된 WAITING 레코드가 있으면 그걸 갱신하고, 없으면(그룹 미지정 세션 등) 새로 생성한다.
     * - 이미 PRESENT/LATE/ABSENT로 처리된 레코드가 있으면 중복 출석으로 간주해 예외.
     */
    @Transactional
    public AttendanceResponse checkIn(Long userId, CheckInRequest request) {
        // 1. NFC 태그 조회 및 활성 상태 확인
        NfcTag nfcTag =
                nfcTagRepository
                        .findByUid(request.getNfcTagUid())
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.NFC_TAG_NOT_FOUND));
        if (!nfcTag.isActive()) {
            throw new BusinessException(ErrorCode.INACTIVE_NFC_TAG);
        }

        // 2. 태그에 연결된 현재 활성 세션 역추적 (요청 바디엔 sessionId 없음)
        //    정상 운영 시 활성 세션은 1개, 다수면 첫 번째 사용
        AttendanceSession session =
                sessionRepository.findActiveSessionsByNfcTagId(nfcTag.getId()).stream()
                        .findFirst()
                        .orElseThrow(() -> new BusinessException(ErrorCode.SESSION_NOT_ACTIVE));

        // 3. 출석/지각 판정 - session.isLate() 기준
        LocalDateTime checkInTime = LocalDateTime.now();
        AttendanceStatus status =
                session.isLate(checkInTime) ? AttendanceStatus.LATE : AttendanceStatus.PRESENT;

        // 4. 분산 락으로 감싼 임계 구역 - 기존 레코드 조회 + 저장/갱신
        //    동시 체크인 부하 테스트에서, 여러 요청이 동시에 findByUserIdAndSessionId에서 "없음"을 보고
        //    각자 save()를 시도해 DB Unique 제약 위반이 그대로 500으로 노출되는 경합이 확인됐다
        //    (concepts.md 참고). 락은 이 구간만 감싼다 - NFC 태그 검증/세션 조회(1~3단계)까지 락으로
        //    감싸면 락을 쥐고 있는 시간이 길어져 다른 요청까지 불필요하게 느려진다 (락 키가 사용자+세션
        //    단위라 다른 사용자는 어차피 안 겹치지만).
        AttendanceRecord attendanceRecord =
                findOrCreateRecordWithLock(userId, session, status, checkInTime, nfcTag);

        // 4-1. 이 세션의 대시보드 캐시(sessionDashboard) 무효화
        //    체크인으로 방금 대시보드 집계 숫자(출석/지각 수 등)가 바뀌었으니, TTL(5초)이 끝나길 기다리지 않고 즉시 지운다.
        //    WebSocket 실시간 푸시는 커밋 후(AFTER_COMMIT)에 재조회하지만, 이 캐시 삭제는 "삭제만" 할 뿐 값을
        //    읽어서 내보내는 게 아니라서 롤백돼도 위험하지 않다 - 최악의 경우 캐시가 불필요하게 한 번 더 비워질 뿐이다.
        cacheManager.getCache(RedisConfig.CACHE_SESSION_DASHBOARD).evict(session.getId());

        // 5. 부가 처리 - 더티 체킹으로 반영됨 (같은 트랜잭션 내 managed 엔티티)
        nfcTag.markUsed(); // 태그 마지막 사용시각 갱신
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        user.recordFirstAttendanceIfAbsent(checkInTime); // 사용자 첫 출석 시각 기록

        // 6. 실시간 푸시 트리거 - 이 시점은 아직 트랜잭션 커밋 전이라 값을 직접 싣지 않고 PK만 이벤트로 발행한다.
        //    실제 조회/전송은 AttendanceEventListener가 트랜잭션이 커밋된 뒤(AFTER_COMMIT)에 수행한다.
        eventPublisher.publishEvent(
                new AttendanceCheckedInEvent(session.getId(), attendanceRecord.getId()));

        return AttendanceResponse.from(attendanceRecord, user);
    }

    /**
     * checkIn()의 "기존 레코드 조회 → 저장/갱신" 구간을 Redisson 분산 락으로 감싼 헬퍼.
     * 락 키를 사용자+세션 단위로 잡아서, 같은 사람이 같은 세션에 짧은 시간 안에 여러 번 요청을
     * 보내도(중복 클릭, 앱 재시도, 네트워크 재전송 등) 이 구간은 한 번에 한 스레드만 통과한다.
     */
    private AttendanceRecord findOrCreateRecordWithLock(
            Long userId,
            AttendanceSession session,
            AttendanceStatus status,
            LocalDateTime checkInTime,
            NfcTag nfcTag) {
        RLock lock = redissonClient.getLock(LOCK_KEY_PREFIX + userId + ":" + session.getId());
        boolean acquired;
        try {
            // waitTime(3초): 락을 못 얻으면 3초까지만 기다리고 포기한다 - 체크인은 원래 즉시 끝나야 하는
            //   작업이라, 오래 기다리게 하느니 빨리 "지금 처리 중이니 다시 시도해라" 응답을 주는 게 낫다.
            // leaseTime을 명시하지 않은 이유(=워치독 활성화): 이 락은 releaseLockAfterTransaction()에서
            //   실제 트랜잭션 커밋 이후에야 풀리기 때문에, 실제 점유 시간이 checkIn() 전체(DB 커넥션 풀
            //   경합 등으로 부하 상황에선 들쭉날쭉해질 수 있음)만큼 늘어난다. 이런 상황에서 leaseTime을
            //   고정값(예: 3초)으로 주면, 실제 처리 시간이 그 값에 근접/초과할 때 아직 안 끝났는데도 Redis가
            //   락을 먼저 강제로 만료시켜버려 다른 스레드가 끼어드는 사고가 날 수 있다(실제로 부하 테스트에서
            //   재현됨). Redisson의 워치독은 락을 쥔 동안 만료 시간을 자동으로 계속 연장해줘서 이 문제를
            //   없앤다 - 서버가 진짜로 죽으면(연장이 멈추면) 기본 30초 뒤에 자동 해제되는 안전장치는 그대로
            //   유지된다.
            acquired = lock.tryLock(LOCK_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
        if (!acquired) {
            // 락 획득 실패 = 지금 이 사용자의 같은 세션 체크인이 이미 처리 중이라는 뜻.
            // DB까지 안 가고 여기서 바로 409로 응답해서 불필요한 경합/부하를 원천 차단한다.
            throw new BusinessException(ErrorCode.CHECKIN_IN_PROGRESS);
        }

        // 락을 획득한 순간부터는 이후 어떤 경로로 메서드를 벗어나든(정상 반환/예외 모두) 반드시 한 번은
        // 풀리도록 해제 시점을 미리 등록해둔다. 여기서 곧바로 unlock()하지 않는 이유는 아래
        // releaseLockAfterTransaction() 주석 참고.
        releaseLockAfterTransaction(lock);

        return attendanceRepository
                .findByUserIdAndSessionId(userId, session.getId())
                .map(existing -> {
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
     * 락 해제 시점을 "이 메서드가 끝나는 시점"이 아니라 "이 메서드를 호출한 트랜잭션이 실제로 끝나는
     * 시점"으로 미룬다.
     *
     * checkIn()은 @Transactional이라 실제 커밋은 Spring 프록시가 메서드 호출 전체를 감싸고 있다가
     * checkIn()이 완전히 return한 "이후"에 처리한다. 만약 여기서 곧바로 unlock()을 호출하면, 락은 풀렸지만
     * 아직 커밋 전인 구간(캐시 무효화, nfcTag/user 갱신, 이벤트 발행 등 checkIn()의 남은 단계)이 그대로
     * 남아있어 다른 요청이 그 틈에 락을 잡고 "아직 커밋 안 된 상태(레코드 없음)"를 보고 또 INSERT를 시도하는
     * 경합이 그대로 재현된다 - 실제로 동시 체크인 20건 부하 테스트에서 이 문제가 500 에러로 확인됐다
     * (평균 응답시간이 200ms대라 이 틈이 "무시할 수준"이 아니었다).
     *
     * afterCompletion 콜백으로 해제를 미루면, 다음 요청은 반드시 "이전 트랜잭션이 커밋을 마친 뒤"에만 락을
     * 잡을 수 있게 되어 이 경합이 사라진다.
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
            // 실제 Spring 트랜잭션 없이 서비스 메서드가 직접 호출되는 경우(예: Mockito 기반 단위 테스트) -
            // 기다릴 커밋 자체가 없으므로 곧바로 해제한다.
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /** 내 출석 기록 조회 (STUDENT, 페이징) */
    public Page<AttendanceResponse> getMyAttendances(Long userId, Pageable pageable) {
        User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        // 본인 기록이므로 User는 1회만 조회해 재사용
        return attendanceRepository
                .findByUserId(userId, pageable)
                .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, user));
    }

    /** 세션별 출석 현황 조회 (ADMIN) */
    public List<AttendanceResponse> getSessionAttendances(Long sessionId) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND);
        }
        List<AttendanceRecord> records = attendanceRepository.findBySessionId(sessionId);

        // N+1 방지: 레코드의 userId를 모아 User를 한 번에 조회 후 Map으로 매핑
        List<Long> userIds = records.stream().map(AttendanceRecord::getUserId).distinct().toList();
        Map<Long, User> userMap =
                userRepository.findAllById(userIds).stream()
                        .collect(Collectors.toMap(User::getId, user -> user));

        return records.stream()
                .map(attendanceRecord -> AttendanceResponse.from(attendanceRecord, userMap.get(attendanceRecord.getUserId())))
                .toList();
    }

    /** 출석 상태 수동 수정 (ADMIN) - modifiedBy는 인증된 관리자 이름 */
    @Transactional
    public AttendanceResponse updateStatus(
            Long attendanceId, AttendanceStatusUpdateRequest request, String modifiedBy) {
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));

        // 도메인 메서드로 상태 변경 (누가/왜 바꿨는지 함께 기록)
        attendanceRecord.modifyStatus(request.getStatus(), modifiedBy, request.getModifyReason());

        // 관리자가 수동으로 상태를 바꾼 직후 대시보드가 옛날 숫자를 보여주면 "방금 바꿨는데 왜 반영이 안 되지"로
        // 보이기 쉬워서, checkIn()과 동일하게 즉시 캐시를 비운다.
        cacheManager.getCache(RedisConfig.CACHE_SESSION_DASHBOARD).evict(attendanceRecord.getSessionId());

        User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
        return AttendanceResponse.from(attendanceRecord, user);
    }

    /** 출석 기록 삭제 (ADMIN) */
    @Transactional
    public void deleteAttendance(Long attendanceId) {
        // existsById 대신 findById로 바꾼 이유: 삭제 전 sessionId를 알아야 그 세션의 대시보드 캐시를 지울 수 있다
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
        attendanceRepository.deleteById(attendanceId);
        cacheManager.getCache(RedisConfig.CACHE_SESSION_DASHBOARD).evict(attendanceRecord.getSessionId());
    }

    /**
     * 세션별 출석 대시보드 (ADMIN) - 상태별 레코드 수 + 대상자 수(targetCount) 집계
     * - targetCount: 세션 groupName 기준 STUDENT 수. 그룹 미지정 세션은 totalRecords로 근사(AttendanceDashboardResponse에서 처리)
     * - Redis에 5초 TTL로 캐싱(RedisConfig 참고) + checkIn/updateStatus/delete/세션종료 시점에 수동 무효화도 같이 해서,
     *   폴링 중 최대 5초 지연은 감수하되 "직접 조작한 직후"만큼은 바로 반영되게 한다.
     */
    @Cacheable(cacheNames = RedisConfig.CACHE_SESSION_DASHBOARD)
    public AttendanceDashboardResponse getSessionDashboard(Long sessionId) {
        AttendanceSession session =
                sessionRepository
                        .findById(sessionId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.SESSION_NOT_FOUND));

        long total = attendanceRepository.countBySessionId(sessionId);
        long present = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.PRESENT);
        long late = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.LATE);
        long absent = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.ABSENT);
        long waiting = attendanceRepository.countBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);
        long targetCount =
                session.getGroupName() != null
                        ? userRepository.countByRoleAndGroupName(UserRole.STUDENT, session.getGroupName())
                        : 0L;

        return AttendanceDashboardResponse.of(sessionId, targetCount, total, present, late, absent, waiting);
    }

    /**
     * 출석 레코드 단건 상세 조회 - AttendanceEventListener가 체크인 커밋 후 실시간 푸시 페이로드를 만들 때 사용한다.
     * (이벤트 발행 시점 값이 아니라 커밋이 확정된 뒤의 최신 상태를 다시 읽기 위함, 섹션 12 참고)
     */
    public AttendanceResponse getAttendanceRecord(Long attendanceId) {
        AttendanceRecord attendanceRecord =
                attendanceRepository
                        .findById(attendanceId)
                        .orElseThrow(() -> new EntityNotFoundException(ErrorCode.ATTENDANCE_NOT_FOUND));
        User user = userRepository.findById(attendanceRecord.getUserId()).orElse(null);
        return AttendanceResponse.from(attendanceRecord, user);
    }

    /**
     * 세션 시작 시 대상 그룹 학생 전원에게 WAITING 레코드 사전 생성 (SessionService.startSession에서 호출)
     * - 그룹 미지정 세션(groupName == null)은 사전 등록 대상이 없으므로 스킵
     * - 이미 레코드가 있는 사용자는 건너뜀 (재시작 등으로 중복 호출되어도 안전)
     */
    @Transactional
    public void initializeWaitingRecords(AttendanceSession session) {
        if (session.getGroupName() == null) {
            return;
        }
        List<User> targets =
                userRepository.findByRoleAndGroupName(UserRole.STUDENT, session.getGroupName());
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

    /**
     * 세션 종료 시 아직 체크인하지 않은(WAITING) 레코드를 결석(ABSENT)으로 일괄 처리
     * (SessionService.closeSession에서 호출)
     */
    @Transactional
    public void markAbsentForRemainingWaiting(Long sessionId) {
        List<AttendanceRecord> waitingRecords =
                attendanceRepository.findBySessionIdAndStatus(sessionId, AttendanceStatus.WAITING);
        for (AttendanceRecord attendanceRecord : waitingRecords) {
            attendanceRecord.modifyStatus(AttendanceStatus.ABSENT, "SYSTEM", "세션 종료 시 자동 결석 처리");
        }
        // WAITING 여러 건이 한 번에 ABSENT로 바뀌어 대시보드 숫자가 크게 움직이는 시점 - 세션 종료 직후 바로 반영되게 캐시 비움
        cacheManager.getCache(RedisConfig.CACHE_SESSION_DASHBOARD).evict(sessionId);
    }
}