package com.attendance.domain.group.dto;

import com.attendance.domain.group.entity.Group;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 그룹 생성/수정 요청 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GroupRequest {

  @NotBlank(message = "그룹명은 필수입니다")
  @Size(max = 100, message = "그룹명은 100자를 초과할 수 없습니다")
  private String name;

  @Size(max = 255, message = "설명은 255자를 초과할 수 없습니다")
  private String description;

  /** Request DTO를 엔티티로 변환한다. organizationId는 요청 필드가 아닌 파라미터로 받아 다른 단체 지정을 막는다. */
  public Group toEntity(Long organizationId) {
    return Group.builder()
        .organizationId(organizationId)
        .name(name)
        .description(description)
        .build();
  }
}
