package com.attendance.domain.user.service;

import java.time.LocalDateTime;
import com.attendance.domain.user.dto.AuthResponse;
import com.attendance.domain.user.dto.LoginRequest;
import com.attendance.domain.user.dto.TokenRefreshRequest;
import com.attendance.domain.user.entity.RefreshToken;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.repository.RefreshTokenRepository;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import com.attendance.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 비즈니스 로직 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;

  /** 로그인 */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
            userRepository
                    .findByUsername(request.getUsername())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    // 비활성화된 사용자는 로그인 불가 (UserService.deleteUser에서 soft delete 처리된 계정)
    if (!user.getActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_USER);
    }

    String accessToken =
            jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername());

    refreshTokenRepository.deleteByUserId(user.getId());

    refreshTokenRepository.save(
            RefreshToken.builder()
                    .userId(user.getId())
                    .token(refreshToken)
                    // Refresh Token 만료 시각 - DB에서 만료 여부 확인에 사용
                    .expiresAt(LocalDateTime.now().plusSeconds(
                            jwtTokenProvider.getRefreshTokenExpirationSeconds()))
                    .build());

    return AuthResponse.of(
            accessToken, refreshToken, jwtTokenProvider.getAccessTokenExpirationSeconds(), user);
  }

  /** 토큰 갱신 */
  @Transactional
  public AuthResponse refresh(TokenRefreshRequest request) {
    String requestToken = request.getRefreshToken();

    if (!jwtTokenProvider.validateToken(requestToken)) {
      throw new BusinessException(ErrorCode.INVALID_TOKEN);
    }

    RefreshToken savedToken =
            refreshTokenRepository
                    .findByToken(requestToken)
                    .orElseThrow(() -> new BusinessException(ErrorCode.REFRESH_TOKEN_NOT_FOUND));

    User user =
            userRepository
                    .findById(savedToken.getUserId())
                    .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    String newAccessToken =
            jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole().name());

    return AuthResponse.ofAccessToken(
            newAccessToken, jwtTokenProvider.getAccessTokenExpirationSeconds());
  }

  /** 로그아웃 */
  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }
}
