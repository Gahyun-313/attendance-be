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

/** 학생 계정 생성 요청 DTO. ADMIN이 학생 계정을 생성할 때 사용한다. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CreateUserRequest {

  // 학번(= username, 로그인 ID로 사용)
  @NotBlank(message = "학번은 필수입니다")
  @Size(min = 4, max = 20, message = "학번은 4자 이상 20자 이하여야 합니다")
  @Pattern(regexp = "^\\w+$", message = "학번은 영문, 숫자, 언더스코어만 사용 가능합니다")
  private String username;

  // 초기 비밀번호. 관리자가 지정하며, 학생은 최초 로그인 후 변경을 권장한다.
  @NotBlank(message = "비밀번호는 필수입니다")
  @Size(min = 8, message = "비밀번호는 8자 이상이어야 합니다")
  private String password;

  // 이메일(선택)
  @Email(message = "이메일 형식이 올바르지 않습니다")
  private String email;

  // 학생 이름
  @NotBlank(message = "이름은 필수입니다")
  @Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
  private String name;

  // 소속 그룹(예: "A반", "1학년")
  @Size(max = 100, message = "그룹명은 100자를 초과할 수 없습니다")
  private String groupName;

  // 비고(관리자 메모)
  @Size(max = 500, message = "비고는 500자를 초과할 수 없습니다")
  private String note;

  /**
   * Request DTO를 User 엔티티로 변환한다. role은 항상 STUDENT로 고정하고, organizationId는 파라미터로 받아 다른 단체 지정을 막는다.
   */
  public User toEntity(String encodedPassword, Long organizationId) {
    return User.builder()
        .username(username)
        .password(encodedPassword)
        .email(email)
        .name(name)
        .groupName(groupName)
        .note(note)
        .role(UserRole.STUDENT) // 관리자가 생성하는 계정은 항상 학생이다.
        .organizationId(organizationId)
        .build();
  }
}
