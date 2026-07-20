package com.attendance.domain.attendance.dto;

import com.attendance.domain.attendance.entity.AttendanceStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석 상태 수정 요청 DTO (ADMIN) - 변경 사유 필수 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class AttendanceStatusUpdateRequest {

  // 변경할 출석 상태 (PRESENT/LATE/ABSENT/WAITING)
  @NotNull(message = "변경할 출석 상태는 필수입니다")
  private AttendanceStatus status;

  // 변경 사유 - 관리자가 상태를 수정할 때 필수 작성
  @NotBlank(message = "변경 사유는 필수입니다")
  @Size(max = 500, message = "변경 사유는 500자를 초과할 수 없습니다")
  private String modifyReason;
}
