package com.attendance.domain.attendance.repository;

import com.attendance.domain.attendance.entity.AttendanceRecord;
import com.attendance.domain.attendance.entity.AttendanceStatus;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** 출석 기록 Repository */
public interface AttendanceRepository extends JpaRepository<AttendanceRecord, Long> {

    /**
     * 중복 출석 방지 - 체크인 전 동일 user+session 레코드 존재 여부 확인.
     * (DB의 user_id+session_id Unique 제약과 함께 애플리케이션 레벨 1차 방어)
     */
    boolean existsByUserIdAndSessionId(Long userId, Long sessionId);

    /**
     * 내 출석 기록 조회 (페이징) - 앱 "내역" 화면용.
     * 정렬은 Controller에서 Pageable(checkInTime DESC 등)로 전달.
     * (Day 6에 (user_id, check_in_time) 복합 인덱스 + cursor pagination으로 개선 예정 - 포트폴리오 Before/After 항목)
     */
    Page<AttendanceRecord> findByUserId(Long userId, Pageable pageable);

    /** 세션별 출석 현황 조회 (관리자) - 한 세션의 전체 출석 레코드 */
    List<AttendanceRecord> findBySessionId(Long sessionId);

    /** 세션별 전체 출석 레코드 수 - 대시보드 집계용 */
    long countBySessionId(Long sessionId);

    /** 세션별 특정 상태 레코드 수 - 대시보드(출석/지각/결석/대기 수) 집계용 */
    long countBySessionIdAndStatus(Long sessionId, AttendanceStatus status);
}