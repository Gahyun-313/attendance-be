package com.attendance.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 비밀번호 재설정 코드 검증 + 새 비밀번호 적용 요청 DTO - POST /api/auth/password-reset/verify */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetVerifyRequest {

  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "이메일 형식이 올바르지 않습니다")
  private String email;

  @NotBlank(message = "인증 코드는 필수입니다")
  private String code;

  @NotBlank(message = "새 비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
  private String newPassword;
}
