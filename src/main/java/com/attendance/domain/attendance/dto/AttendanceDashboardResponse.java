package com.attendance.domain.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 세션별 출석 대시보드 응답 DTO.
 * 주의: totalRecords는 "출석 레코드 수" 기준. 실제 "대상자 수"는 그룹 멤버 기준이라
 * UserRepository(Day 3) 연동 후 정확해짐. 현재 출석률은 레코드 기반 근사치.
 */
@Getter
@Builder
@AllArgsConstructor
public class AttendanceDashboardResponse {

    private Long sessionId;
    private long totalRecords; // 해당 세션의 전체 출석 레코드 수
    private long present;       // 출석 수
    private long late;          // 지각 수
    private long absent;        // 결석 수
    private long waiting;       // 대기 수
    private double attendanceRate; // (출석 + 지각) / 전체 레코드 * 100 (소수 1자리)

    public static AttendanceDashboardResponse of(
            Long sessionId, long total, long present, long late, long absent, long waiting) {
        // 레코드가 0건이면 0%, 아니면 (출석+지각)/전체 비율을 소수 1자리로 반올림
        double rate = total == 0 ? 0.0 : Math.round((present + late) * 1000.0 / total) / 10.0;
        return AttendanceDashboardResponse.builder()
                .sessionId(sessionId)
                .totalRecords(total)
                .present(present)
                .late(late)
                .absent(absent)
                .waiting(waiting)
                .attendanceRate(rate)
                .build();
    }
}