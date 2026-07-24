package com.attendance.domain.user.service;

import java.time.LocalDateTime;
import com.attendance.domain.organization.entity.Organization;
import com.attendance.domain.organization.repository.OrganizationRepository;
import com.attendance.domain.user.dto.AuthResponse;
import com.attendance.domain.user.dto.EmailJoinVerifyRequest;
import com.attendance.domain.user.dto.LoginRequest;
import com.attendance.domain.user.dto.OAuthLoginRequest;
import com.attendance.domain.user.dto.TokenRefreshRequest;
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
  private final OrganizationRepository organizationRepository;
  private final JwtTokenProvider jwtTokenProvider;
  private final PasswordEncoder passwordEncoder;
  private final GoogleOAuthClient googleOAuthClient;
  private final KakaoOAuthClient kakaoOAuthClient;
  private final EmailVerificationService emailVerificationService;

  /** 로그인 */
  @Transactional
  public AuthResponse login(LoginRequest request) {
    User user =
            userRepository
                    .findByUsername(request.getUsername())
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_CREDENTIALS));

    // 소셜 로그인 전용 계정(password == null)은 이 경로로 로그인할 수 없음 - 비밀번호가 아예 없기 때문
    if (user.getPassword() == null || !passwordEncoder.matches(request.getPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
    }

    // 비활성화된 사용자는 로그인 불가 (UserService.deleteUser에서 soft delete 처리된 계정)
    if (!user.getActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_USER);
    }

    return issueTokens(user);
  }

  /**
   * 소셜 로그인 (구글/카카오) POST /api/auth/oauth/{provider}
   *
   * <p>흐름: (1) 제공자 토큰으로 사용자 정보 조회 → (2) provider+providerId로 이미 연결된 계정이
   * 있으면 바로 로그인 → (3) 없으면 신규 조인 - organizationCode로 단체를 확인하고 ADMIN 계정을
   * 새로 만든 뒤 로그인 처리. 최초 단체 생성/최초 어드민은 여전히 운영자가 수동 생성하고(변경 없음),
   * 이 경로는 "추가 어드민"이 단체코드를 들고 셀프 조인하는 흐름이다 (multi-tenancy-plan.md 참고).
   *
   * @param provider "google" 또는 "kakao" (대소문자 무관)
   */
  @Transactional
  public AuthResponse oauthLogin(String provider, OAuthLoginRequest request) {
    String normalizedProvider = provider.toUpperCase();
    OAuthUserInfo userInfo = fetchOAuthUserInfo(normalizedProvider, request.getToken());

    User user =
            userRepository
                    .findByProviderAndProviderId(normalizedProvider, userInfo.getProviderId())
                    .orElseGet(() -> joinNewOAuthAdmin(normalizedProvider, userInfo, request.getOrganizationCode()));

    if (!user.getActive()) {
      throw new BusinessException(ErrorCode.INACTIVE_USER);
    }

    return issueTokens(user);
  }

  /**
   * 이메일 인증으로 단체 조인 (소셜 로그인 대체 경로) POST /api/auth/join/email/verify
   *
   * <p>회사 네트워크 등에서 소셜 로그인 서비스 자체가 차단돼 있을 때를 대비한 경로 - 미리 발급받은
   * 인증 코드(EmailVerificationService.sendVerificationCode)를 검증해 organizationId를 얻고,
   * 사용자가 직접 지정한 비밀번호로 ADMIN 계정을 생성한다. 생성 즉시 로그인 처리(토큰 발급)까지 한다.
   */
  @Transactional
  public AuthResponse joinByEmail(EmailJoinVerifyRequest request) {
    Long organizationId = emailVerificationService.verifyAndConsume(request.getEmail(), request.getCode());

    // 인증 코드 발급 시점에도 중복 체크를 하지만, 발급 후 검증 전 사이에 다른 경로로 가입됐을 수 있어 재확인
    if (userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    User user =
            userRepository.save(
                    User.builder()
                            .username(request.getEmail()) // 소셜/이메일 조인 계정은 이메일을 username으로 사용
                            .password(passwordEncoder.encode(request.getPassword()))
                            .email(request.getEmail())
                            .name(request.getName())
                            .role(UserRole.ADMIN)
                            .organizationId(organizationId)
                            .passwordChanged(true) // 본인이 직접 지정한 비밀번호라 "최초 변경 안내" 대상 아님
                            .build());

    return issueTokens(user);
  }

  // ------------------------------------------------
  // 내부 유틸
  // ------------------------------------------------

  /** 제공자별 사용자 정보 조회 분기 */
  private OAuthUserInfo fetchOAuthUserInfo(String normalizedProvider, String token) {
    return switch (normalizedProvider) {
      case "GOOGLE" -> googleOAuthClient.getUserInfo(token);
      case "KAKAO" -> kakaoOAuthClient.getUserInfo(token);
      default -> throw new BusinessException(ErrorCode.UNSUPPORTED_OAUTH_PROVIDER);
    };
  }

  /** 아직 연결된 계정이 없는 소셜 로그인 - organizationCode를 검증하고 신규 ADMIN 계정 생성 */
  private User joinNewOAuthAdmin(String provider, OAuthUserInfo userInfo, String organizationCode) {
    if (organizationCode == null || organizationCode.isBlank()) {
      throw new BusinessException(ErrorCode.ORGANIZATION_CODE_REQUIRED);
    }
    Organization organization =
            organizationRepository
                    .findByCode(organizationCode)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_ORGANIZATION_CODE));

    // 같은 이메일로 이미 password 계정 등 다른 계정이 존재하면 충돌 - provider/providerId만
    // 다르고 이메일이 같은 경우까지는 계정 통합(연동)하지 않고 명확히 막는다 (범위 밖으로 미룸)
    if (userRepository.existsByEmail(userInfo.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    return userRepository.save(
            User.builder()
                    .username(userInfo.getEmail())
                    .password(null) // 소셜 로그인 전용 계정 - 비밀번호 로그인 경로 자체를 안 씀
                    .email(userInfo.getEmail())
                    .name(userInfo.getName())
                    .role(UserRole.ADMIN)
                    .organizationId(organization.getId())
                    .provider(provider)
                    .providerId(userInfo.getProviderId())
                    .passwordChanged(true) // 비밀번호가 없으니 "변경 안내" 대상 아님
                    .build());
  }

  /** Access/Refresh Token 발급 + 기존 Refresh Token 교체 - login/oauthLogin/joinByEmail 공용 */
  private AuthResponse issueTokens(User user) {
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