package com.attendance.domain.user.dto;

import com.attendance.domain.user.entity.User;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 사용자 응답 DTO */
@Getter
@Builder
@AllArgsConstructor
public class UserResponse {

  private Long id;
  private String username;
  private String email;
  private String name;
  private String role;
  private String groupName;
  private String note;
  private Boolean passwordChanged;
  private Boolean active;
  private LocalDateTime firstAttendanceAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  public static UserResponse from(User user) {
    return UserResponse.builder()
        .id(user.getId())
        .username(user.getUsername())
        .email(user.getEmail())
        .name(user.getName())
        .role(user.getRole().name())
        .groupName(user.getGroupName())
        .note(user.getNote())
        .passwordChanged(user.getPasswordChanged())
        .active(user.getActive())
        .firstAttendanceAt(user.getFirstAttendanceAt())
        .createdAt(user.getCreatedAt())
        .updatedAt(user.getUpdatedAt())
        .build();
  }
}
