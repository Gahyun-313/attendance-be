package com.attendance.domain.attendance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 세션별 출석 대시보드 응답 DTO.
 * targetCount는 세션의 groupName 기준 대상 학생 수(UserRepository 연동, Day 3),
 * totalRecords는 실제 생성된 출석 레코드 수(WAITING 포함). 그룹 미지정 세션은 targetCount를 totalRecords로 근사한다.
 */
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor // Redis 캐시 역직렬화가 기본 생성자로 객체를 먼저 만든 뒤 필드를 채우는 방식이라서 필요함
public class AttendanceDashboardResponse {

    private Long sessionId;
    private long targetCount;   // 대상자 수 (세션 groupName 기준 학생 수, 그룹 미지정 시 totalRecords로 근사)
    private long totalRecords; // 해당 세션의 전체 출석 레코드 수
    private long present;       // 출석 수
    private long late;          // 지각 수
    private long absent;        // 결석 수
    private long waiting;       // 대기 수
    private double attendanceRate; // (출석 + 지각) / 대상자 수(targetCount) * 100 (소수 1자리)

    public static AttendanceDashboardResponse of(
            Long sessionId, long targetCount, long total, long present, long late, long absent, long waiting) {
        // 출석률 산출 기준: targetCount가 있으면 그 기준, 없으면(그룹 미지정 세션) totalRecords로 근사
        long base = targetCount > 0 ? targetCount : total;
        double rate = base == 0 ? 0.0 : Math.round((present + late) * 1000.0 / base) / 10.0;
        return AttendanceDashboardResponse.builder()
                .sessionId(sessionId)
                .targetCount(targetCount)
                .totalRecords(total)
                .present(present)
                .late(late)
                .absent(absent)
                .waiting(waiting)
                .attendanceRate(rate)
                .build();
    }
}