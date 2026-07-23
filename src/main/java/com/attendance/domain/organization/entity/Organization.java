package com.attendance.domain.organization.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 단체(학교/학원) 엔티티.
 * 멀티테넌시의 기준 단위 - User, AttendanceSession 등이 이 엔티티를 FK로 참조해 서로 다른 단체의
 * 데이터를 격리한다.
 */
@Entity
@Table(name = "organizations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Organization {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    // 추가 어드민이 소셜 로그인으로 셀프 조인할 때 쓰는 초대 코드다.
    // 특정 어드민 계정의 아이디와는 별개의 값으로 관리한다.
    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public Organization(String name, String code, Boolean active) {
        this.name = name;
        this.code = code;
        this.active = active != null ? active : true;
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

    public void updateInfo(String name) {
        if (name != null) this.name = name;
    }

    // 초대 코드 재발급 - 코드가 유출됐을 때 기존 코드를 무효화하는 용도.
    public void reissueCode(String newCode) {
        this.code = newCode;
    }

    public void activate() {
        this.active = true;
    }

    public void deactivate() {
        this.active = false;
    }

    public boolean isActive() {
        return this.active;
    }
}