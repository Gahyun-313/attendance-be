package com.attendance.domain.session;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
/**
 * 출석 세션 엔티티
 * 하나의 수업 출석 체크 단위
 */
@Entity
@Table(name = "attendance_sessions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AttendanceSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 500)
    private String description;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalDateTime endTime;

    @Column(name = "late_threshold_minutes", nullable = false)
    private Integer lateThresholdMinutes = 10;

    @Column(length = 100)
    private String location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SessionStatus status;

    @Column(name = "nfc_tag_id")
    private Long nfcTagId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /**
     * 세션이 현재 활성 상태인지 확인
     */
    public boolean isActive() {
        LocalDateTime now = LocalDateTime.now();
        return status == SessionStatus.ACTIVE &&
                now.isAfter(startTime) &&
                now.isBefore(endTime);
    }

    /**
     * 주어진 시간이 지각인지 확인
     */
    public boolean isLate(LocalDateTime checkInTime) {
        return checkInTime.isAfter(startTime.plusMinutes(lateThresholdMinutes));
    }
}
