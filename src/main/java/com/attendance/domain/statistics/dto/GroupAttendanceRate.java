package com.attendance.domain.statistics.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 그룹별 출석 현황 항목 - DashboardStatisticsResponse의 groupAttendanceRates 리스트 원소 */
@Getter
@Builder
@AllArgsConstructor
public class GroupAttendanceRate {

    private String groupName;
    private long targetCount;   // 그룹 소속 학생 수
    private long presentCount;  // 완료된 세션들의 출석 누적 건수
    private long lateCount;
    private long absentCount;
    private double attendanceRate; // (출석+지각)/(출석+지각+결석) * 100

    public static GroupAttendanceRate of(
            String groupName, long targetCount, long present, long late, long absent) {
        long base = present + late + absent;
        double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
        return GroupAttendanceRate.builder()
                .groupName(groupName)
                .targetCount(targetCount)
                .presentCount(present)
                .lateCount(late)
                .absentCount(absent)
                .attendanceRate(rate)
                .build();
    }
}