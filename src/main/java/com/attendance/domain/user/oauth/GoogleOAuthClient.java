package com.attendance.domain.user.oauth;

import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 구글 OAuth 사용자 정보 조회
 * 프론트가 구글 SDK로 받은 ID Token을 그대로 넘겨받아, 구글의 tokeninfo 엔드포인트에 검증을 위임한다.
 * JWT 서명 검증 라이브러리를 직접 들이지 않아도 되는 대신, 로그인마다 구글에 네트워크 호출이 한 번 생긴다
 * (로그인 빈도를 감안하면 감수할 만한 트레이드오프다).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GoogleOAuthClient {

  private static final String TOKEN_INFO_URL = "https://oauth2.googleapis.com/tokeninfo?id_token=";

  private final RestTemplate restTemplate;

  // 구글 클라우드 콘솔에서 발급받은 OAuth 클라이언트 ID다. ID Token의 aud(발급 대상)가 이 값과
  // 일치해야 "우리 앱이 요청해서 받은 토큰"임을 확인할 수 있다(다른 앱용으로 발급된 토큰 재사용을 막는다).
  @Value("${oauth.google.client-id}")
  private String clientId;

  /** ID Token 검증 요청 및 검증된 사용자 정보 반환 */
  public OAuthUserInfo getUserInfo(String idToken) {
    // 구글 tokeninfo 엔드포인트에 토큰 검증 위임
    Map<String, String> response;
    try {
      response = restTemplate.getForObject(TOKEN_INFO_URL + idToken, Map.class);
    } catch (RestClientException e) {
      log.error("Google tokeninfo 호출 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    // aud가 우리 앱의 클라이언트 ID와 다르면, 다른 곳에서 발급된 토큰일 수 있어 신뢰하지 않는다.
    if (response == null || !clientId.equals(response.get("aud"))) {
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    String sub = response.get("sub");
    String email = response.get("email");
    if (sub == null || email == null) {
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    String name = response.getOrDefault("name", email);
    return new OAuthUserInfo(sub, email, name);
  }
}
