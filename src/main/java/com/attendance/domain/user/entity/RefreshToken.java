package com.attendance.domain.user.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Refresh Token 엔티티
 * JWT Access Token이 만료되었을 때 새로 발급받기 위한 토큰
 * 서버에 저장하여 보안을 강화하고 로그아웃 시 무효화 가능
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // JWT Refresh Token 문자열
    @Column(nullable = false, unique = true, length = 500)
    private String token;

    // 이 토큰의 소유자 (User ID)
    @Column(name = "user_id", nullable = false)
    private Long userId;

    // 토큰 만료 시간
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    // 토큰 생성 시간
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    /**
     * 토큰이 만료되었는지 확인
     * @return true: 만료됨, false: 유효함
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }
}