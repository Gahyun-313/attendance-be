package com.attendance.domain.user.dto;

import com.attendance.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 사용자 응답 DTO
 * - 사용자 정보 조회 시 클라이언트에게 반환하는 응답 객체
 */
@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

    // 사용자 고유 ID
    private Long id;
    // 사용자 아이디
    private String username;
    // 이메일 주소
    private String email;
    // 사용자 이름
    private String name;
    // 사용자 권한
    private String role;
    // 계정 생성 일시
    private LocalDateTime createdAt;
    // 계정 정보 수정 일시
    private LocalDateTime updatedAt;

    // User 엔티티를 UserResponse DTO로 변환
    public static UserResponse from(User user) {
        return UserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())    // Enum을 String으로 변환
                .createdAt(user.getCreatedAt())
                .updatedAt(user.getUpdatedAt())
                .build();
    }
}