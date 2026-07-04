package com.attendance.domain.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 정보 수정 요청 DTO - ADMIN이 학생 정보를 수정할 때 사용 (PUT /api/users/{userId})
 * - username, password, role은 수정 대상에서 제외 (username은 로그인 ID로 불변, 비밀번호는 별도 API로 분리)
 * - 전달되지 않은(null) 필드는 기존 값 유지 (부분 수정)
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class UserUpdateRequest {

    // 이메일 (선택)
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    // 이름
    @Size(max = 50, message = "이름은 50자를 초과할 수 없습니다")
    private String name;

    // 소속 그룹 (예: "A반", "1학년")
    @Size(max = 100, message = "그룹명은 100자를 초과할 수 없습니다")
    private String groupName;

    // 비고 - 관리자 메모
    @Size(max = 500, message = "비고는 500자를 초과할 수 없습니다")
    private String note;
}