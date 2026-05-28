package com.attendance.domain.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 로그인 요청 DTO - 클라이언트로부터 로그인 인증 정보를 받아오는 데이터 전송 객체 */
@Getter
@NoArgsConstructor // JSON 역직렬화를 위한 기본 생성자
@AllArgsConstructor // 테스트 등을 위한 전체 생성자
public class LoginRequest {

  // 로그인 아이디
  @NotBlank(message = "아이디는 필수입니다")
  private String username;

  // 로그인 비밀번호 (평문)
  // 서버에서 암호화된 비밀번호와 비교 검증
  @NotBlank(message = "비밀번호는 필수입니다")
  private String password;
}
