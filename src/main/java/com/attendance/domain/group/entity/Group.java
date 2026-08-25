package com.attendance.domain.group.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 그룹 마스터 엔티티. User/Session의 문자열 groupName이 실제 존재하는 값인지 검증하는 기준 목록으로 사용한다. */
@Entity
@Table(
    name = "groups",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_groups_organization_name",
            columnNames = {"organization_id", "name"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Group {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  @Column(nullable = false, length = 100)
  private String name;

  @Column(length = 255)
  private String description;

  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  @Builder
  public Group(Long organizationId, String name, String description) {
    this.organizationId = organizationId;
    this.name = name;
    this.description = description;
  }

  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  public void updateInfo(String name, String description) {
    if (name != null) this.name = name;
    if (description != null) this.description = description;
  }
}
