package com.attendance.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

/**
 * 사용자 엔티티
 * Spring Security의 UserDetails를 구현하여 인증 시스템과 통합
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 로그인에 사용되는 고유 아이디
    @Column(nullable = false, unique = true, length = 50)
    private String username;

    // 암호화된 비밀번호
    @Column(nullable = false)
    private String password;

    // 실제 이름
    @Column(nullable = false, length = 100)
    private String name;

    // 이메일 (선택)
    @Column(unique = true, length = 100)
    private String email;

    // 전화번호 (선택)
    @Column(length = 20)
    private String phone;

    // 사용자 역할 (STUDENT/ADMIN)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserRole role;

    // 소속 (학과, 동아리 등)
    @Column(length = 100)
    private String organization;

    // 학번
    @Column(name = "student_id", length = 20)
    private String studentId;

    // FCM 푸시 알림 토큰
    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    // 계정 활성화 여부
    @Column(nullable = false)
    private Boolean enabled = true;

    // 생성 시간
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    // 수정 시간
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // 엔티티가 처음 저장될 때 자동으로 시간 설정
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    // 엔티티가 업데이트될 때 자동으로 시간 설정
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // === UserDetails 인터페이스 구현 (Spring Security용) ===

    /**
     * 사용자의 권한 목록 반환
     * ROLE_ 접두사를 붙여서 Spring Security 컨벤션을 따름
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    /**
     * 계정이 만료되지 않았는지 (true = 만료 안됨)
     */
    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    /**
     * 계정이 잠기지 않았는지 (true = 잠기지 않음)
     */
    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    /**
     * 비밀번호가 만료되지 않았는지 (true = 만료 안됨)
     */
    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    /**
     * 계정이 활성화되어 있는지
     */
    @Override
    public boolean isEnabled() {
        return enabled;
    }
}