package com.attendance.domain.user.dto;

import com.attendance.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * 인증 응답 DTO
 * - 로그인 성공 / 토큰 갱신 시 클라이언트에게 반환하는 응답 객체
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthResponse {

    // JWT Access Token - 실제 API 요청 시 사용하는 토큰 (짧은 만료 시간)
    private String accessToken;
    // JWT Refresh Token - Access Token 갱신용 토큰 (긴 만료 시간)
    private String refreshToken;
    // 토큰 타입 - "Bearer" 고정값
    private String tokenType;
    // Access Token 만료 시간 (초 단위)
    private long expiresIn;
    // 사용자 정보 - 로그인 성공 시에만 포함, 토큰 갱신 시에는 null
    private UserInfo user;

    /**
     * 로그인 응답 생성
     * - Access Token, Refresh Token, 사용자 정보를 모두 포함
     */
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, User user) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .user(UserInfo.from(user)) // User 엔티티를 UserInfo DTO로 변환
                .build();
    }

    /**
     * 토큰 갱신 응답 생성
     * - Access Token만 새로 발급하고, Refresh Token과 사용자 정보는 포함하지 않음
     */
    public static AuthResponse ofAccessToken(String accessToken, long expiresIn) {
        return AuthResponse.builder()
                .accessToken(accessToken)
                .tokenType("Bearer")
                .expiresIn(expiresIn)
                .build();
    }

    /**
     * 사용자 정보 내부 클래스 (인증 응답용)
     */
    @Getter
    @Builder
    @AllArgsConstructor
    public static class UserInfo {
        private Long id;        // 사용자 고유 ID
        private String username; // 사용자 아이디
        private String name;    // 사용자 이름
        private String role;    // 사용자 권한

        // User 엔티티를 UserInfo DTO로 변환
        public static UserInfo from(User user) {
            return UserInfo.builder()
                    .id(user.getId())
                    .username(user.getUsername())
                    .name(user.getName())
                    .role(user.getRole().name()) // Enum을 String으로 변환
                    .build();
        }
    }
}
