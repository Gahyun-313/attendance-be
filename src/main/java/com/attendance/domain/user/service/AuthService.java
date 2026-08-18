package com.attendance.domain.user.service;

import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.domain.user.dto.*;
import com.attendance.domain.user.entity.RefreshToken;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.oauth.GoogleOAuthClient;
import com.attendance.domain.user.oauth.KakaoOAuthClient;
import com.attendance.domain.user.oauth.OAuthUserInfo;
import com.attendance.domain.user.repository.RefreshTokenRepository;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import com.attendance.global.security.JwtTokenProvider;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 인증 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

  private final UserRepository userRepository;
  private final RefreshTokenRepository refreshTokenRepository;
  private final OrganizationRepository organizationRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final GoogleOAuthClient googleOAuthClient;
  private final KakaoOAuthClient kakaoOAuthClient;
  private final EmailVerificationService emailVerificationService;

  /** 로그인 처리 */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
        userRepository
            .findByUsername(request.getUsername())
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    // 소셜 로그인 전용 계정(password == null)은 비밀번호 자체가 없어 이 경로로 로그인할 수 없다.
    if (user.getPassword() == null
        || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    // 비활성화된 사용자는 로그인할 수 없다. UserService.deleteUser로 soft delete된 계정이다.
    if (!user.getActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_USER);
    }

    return issueTokens(user);
  }

  /**
   * 소셜 로그인 처리(구글/카카오, provider는 "google"/"kakao" 대소문자 무관)
   * 제공자 토큰으로 사용자를 조회해 연결된 계정이 있으면 로그인시키고, 없으면 organizationCode로
   * 단체를 확인한 뒤 신규 ADMIN 계정을 생성한다(셀프 조인). 최초 단체/최초 어드민은 운영자가 수동 생성한다.
   */
  @Transactional
  public AuthResponse oauthLogin(String provider, OAuthLoginRequest request) {
    String normalizedProvider = provider.toUpperCase();
    OAuthUserInfo userInfo = fetchOAuthUserInfo(normalizedProvider, request.getToken());

    User user =
        userRepository
            .findByProviderAndProviderId(normalizedProvider, userInfo.getProviderId())
            .orElseGet(
                () ->
                    joinNewOAuthAdmin(normalizedProvider, userInfo, request.getOrganizationCode()));

    if (!user.getActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_USER);
    }

    return issueTokens(user);
  }

  /** 이메일 인증으로 단체 조인(소셜 로그인이 막힌 환경 대비). 인증 코드를 검증해 organizationId를 얻고 ADMIN 계정을 생성한 뒤 바로 로그인 처리 */
  @Transactional
  public AuthResponse joinByEmail(EmailJoinVerifyRequest request) {
    Long organizationId =
        emailVerificationService.verifyAndConsume(request.getEmail(), request.getCode());

    // 인증 코드 발급 시점에도 중복을 체크하지만, 발급 후 검증 전 사이에 다른 경로로 가입됐을 수 있어 재확인한다.
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    User user =
        userRepository.save(
            User.builder()
                .username(request.getEmail()) // 소셜/이메일 조인 계정은 이메일을 username으로 쓴다.
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .name(request.getName())
                .role(UserRole.ADMIN)
                .organizationId(organizationId)
                .passwordChanged(true) // 본인이 직접 지정한 비밀번호라 "최초 변경 안내" 대상이 아니다.
                .build());

    return issueTokens(user);
  }

  /** 비밀번호 재설정(로그아웃 상태). 기존 계정의 비밀번호만 변경하며, 자동 로그인은 시키지 않는다. */
  @Transactional
  public void resetPassword(PasswordResetVerifyRequest request) {
    emailVerificationService.verifyPasswordResetCodes(request.getEmail(), request.getCode());

    User user =
        userRepository
            .findByEmail(request.getEmail())
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    user.changePassword(passwordEncoder.encode(request.getNewPassword()));
  }

  // 내부 유틸

  /** 제공자별 사용자 정보 조회 분기 */
  private OAuthUserInfo fetchOAuthUserInfo(String normalizedProvider, String token) {
    return switch (normalizedProvider) {
      case "GOOGLE" -> googleOAuthClient.getUserInfo(token);
      case "KAKAO" -> kakaoOAuthClient.getUserInfo(token);
      default -> throw new BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    };
  }

  /** 아직 연결된 계정이 없는 소셜 로그인 사용자를 위해 organizationCode를 검증하고 신규 ADMIN 계정 생성 */
  private User joinNewOAuthAdmin(String provider, OAuthUserInfo userInfo, String organizationCode) {
    if (organizationCode == null || organizationCode.isBlank()) {
      throw new BusinessException(ErrorCode.ORGANIZATION_CODE_REQUIRED);
    }
    Organization organization =
        organizationRepository
            .findByCode(organizationCode)
            .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ORGANIZATION_CODE));

    // 같은 이메일로 이미 다른 계정(예: password 계정)이 존재하면 충돌로 막는다. provider/providerId만
    // 다르고 이메일이 같은 경우의 계정 통합(연동)은 범위 밖이라 다루지 않는다.
    if (userRepository.existsByEmail(userInfo.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    return userRepository.save(
        User.builder()
            .username(userInfo.getEmail())
            .password(null) // 소셜 로그인 전용 계정이라 비밀번호 로그인 경로 자체를 쓰지 않는다.
            .email(userInfo.getEmail())
            .name(userInfo.getName())
            .role(UserRole.ADMIN)
            .organizationId(organization.getId())
            .provider(provider)
            .providerId(userInfo.getProviderId())
            .passwordChanged(true) // 비밀번호가 없으니 "변경 안내" 대상이 아니다.
            .build());
  }

  /** Access/Refresh 토큰을 발급하고 기존 Refresh Token 교체. login/oauthLogin/joinByEmail에서 공용으로 사용한다. */
  private AuthResponse issueTokens(User user) {
    String accessToken =
        jwtTokenProvider.createAccessToken(user.getId(), user.getUsername(), user.getRole().name());
    String refreshToken = jwtTokenProvider.createRefreshToken(user.getId(), user.getUsername());

    refreshTokenRepository.deleteByUserId(user.getId());

    refreshTokenRepository.save(
        RefreshToken.builder()
            .userId(user.getId())
            .token(refreshToken)
            // Refresh Token 만료 시각. DB에서 만료 여부를 확인할 때 사용한다.
            .expiresAt(
                LocalDateTime.now()
                    .plusSeconds(jwtTokenProvider.getRefreshTokenExpirationSeconds()))
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

  /** 로그아웃 처리 */
  @Transactional
  public void logout(Long userId) {
    refreshTokenRepository.deleteByUserId(userId);
  }
}
