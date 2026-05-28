package com.attendance.domain.user.dto;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 회원가입 요청 DTO - 클라이언트로부터 회원가입 정보를 받아오는 데이터 전송 객체 */
@Getter
@NoArgsConstructor // JSON 역직렬화를 위한 기본 생성자
@AllArgsConstructor // 모든 필드를 받는 생성자
public class SignupRequest {

  // 사용자 아이디
  @NotBlank(message = "아이디는 필수입니다")
  @Size(min = 4, max = 20, message = "아이디는 4자 이상 20자 이하여야 합니다")
  @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "아이디는 영문, 숫자, 언더스코어만 사용 가능합니다")
  private String username;

  // 비밀번호 (평문) - 실제 저장 시에는 암호화되어 저장됨
  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
  private String password;

  // 이메일 주소 - 이메일 형식 검증
  @NotBlank(message = "이메일은 필수입니다")
  @Email(message = "이메일 형식이 올바르지 않습니다")
  private String email;

  // 사용자 이름 - 최대 50자 권한
  @NotBlank(message = "이름은 필수입니다")
  @Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
  private String name;

  // 사용자 권한 (선택적) - null인 경우 기본값으로 STUDENT 역할 부여
  private UserRole role;

  /** Request DTO를 User 엔티티로 변환 */
  public User toEntity(String encodedPassword) {
    return User.builder()
        .username(username)
        .password(encodedPassword) // 암호화된 비밀번호 사용
        .email(email)
        .name(name)
        .role(role != null ? role : UserRole.STUDENT) // null -> STUDENT 기본값
        .build();
  }
}
