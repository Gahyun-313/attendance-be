package com.attendance.domain.group.dto;

import com.attendance.domain.group.entity.Group;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 그룹 응답 DTO */
@Getter
@Builder
@AllArgsConstructor
public class GroupResponse {

  private Long id;
  private String name;
  private String description;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 엔티티를 Response DTO로 변환 */
  public static GroupResponse from(Group group) {
    return GroupResponse.builder()
        .id(group.getId())
        .name(group.getName())
        .description(group.getDescription())
        .createdAt(group.getCreatedAt())
        .updatedAt(group.getUpdatedAt())
        .build();
  }
}
