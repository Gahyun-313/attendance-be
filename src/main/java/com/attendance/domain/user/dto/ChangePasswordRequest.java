package com.attendance.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비밀번호 변경 요청 DTO - PATCH /api/users/me/password 에서 사용 (본인 전용) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

  // 현재 비밀번호 - 본인 확인용
  @NotBlank(message = "현재 비밀번호는 필수입니다")
  private String currentPassword;

  // 새 비밀번호
  @NotBlank(message = "새 비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
  private String newPassword;
}
