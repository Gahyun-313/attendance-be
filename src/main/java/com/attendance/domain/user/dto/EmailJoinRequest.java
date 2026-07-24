package com.attendance.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 이메일 인증 코드 요청 DTO - POST /api/auth/join/email/request */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class EmailJoinRequest {

    @NotBlank(message = "단체 코드는 필수입니다")
    private String organizationCode;

    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;
}