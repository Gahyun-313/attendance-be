package com.attendance.domain.user.service;

import com.attendance.domain.user.dto.AuthResponse;
import com.attendance.domain.user.dto.LoginRequest;
import com.attendance.domain.user.dto.SignupRequest;
import com.attendance.domain.user.dto.TokenRefreshRequest;
import com.attendance.domain.user.dto.UserResponse;
import com.attendance.domain.user.entity.RefreshToken;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.repository.RefreshTokenRepository;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import com.attendance.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 인증 비즈니스 로직
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final PasswordEncoder passwordEncoder;

    @Value("${jwt.access-token-validity}")
    private long accessTokenValidity;

    /**
     * 회원가입
     */
    @Transactional
    public UserResponse signup(SignupRequest request) {
        // username 중복 체크
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_USERNAME);
        }

        // email 중복 체크
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
        }

        // 비밀번호 암호화 후 저장
        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = request.toEntity(encodedPassword);
        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    /**
     * 로그인
     */
    @Transactional
    public AuthResponse login(LoginRequest request) {
        // 사용자 조회
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

        // 비밀번호 검증
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        // 토큰 생성
        String accessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );
        String refreshToken = jwtTokenProvider.createRefreshToken(
                user.getId(), user.getUsername()
        );

        // 기존 RefreshToken 삭제 후 새로 저장
        refreshTokenRepository.deleteByUserId(user.getId());
        refreshTokenRepository.save(RefreshToken.builder()
                .userId(user.getId())
                .token(refreshToken)
                .build());

        return AuthResponse.of(accessToken, refreshToken, accessTokenValidity / 1000, user);
    }

    /**
     * 토큰 갱신
     */
    @Transactional
    public AuthResponse refresh(TokenRefreshRequest request) {
        String requestToken = request.getRefreshToken();

        // RefreshToken 유효성 검증
        if (!jwtTokenProvider.validateToken(requestToken)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        // DB에서 RefreshToken 조회
        RefreshToken savedToken = refreshTokenRepository.findByToken(requestToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

        // 사용자 조회
        User user = userRepository.findById(savedToken.getUserId())
                .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        // 새 AccessToken 발급
        String newAccessToken = jwtTokenProvider.createAccessToken(
                user.getId(), user.getUsername(), user.getRole().name()
        );

        return AuthResponse.ofAccessToken(newAccessToken, accessTokenValidity / 1000);
    }

    /**
     * 로그아웃
     */
    @Transactional
    public void logout(Long userId) {
        // RefreshToken 삭제
        refreshTokenRepository.deleteByUserId(userId);
    }
}