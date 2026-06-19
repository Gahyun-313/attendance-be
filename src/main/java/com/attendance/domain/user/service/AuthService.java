package com.attendance.domain.user.service;

import com.attendance.domain.user.dto.AuthResponse;
import com.attendance.domain.user.dto.CreateUserRequest;
import com.attendance.domain.user.dto.LoginRequest;
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

  /** 학생 계정 생성 (ADMIN 전용) */
  @Transactional
  public UserResponse signup(CreateUserRequest request) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_USERNAME);
    }
    if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    String encodedPassword = passwordEncoder.encode(request.getPassword());
    User user = request.toEntity(encodedPassword);
    User savedUser = userRepository.save(user);

    return UserResponse.from(savedUser);
  }

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

    String accessToken =
            jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername());

    refreshTokenRepository.deleteByUserId(user.getId());
    refreshTokenRepository.save(
            RefreshToken.builder().userId(user.getId()).token(refreshToken).build());

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