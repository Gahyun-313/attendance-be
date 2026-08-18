package com.attendance.domain.user.oauth;

import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.ErrorCode;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 카카오 OAuth 사용자 정보 조회
 * 카카오는 ID Token 대신 REST API 방식이 표준이라, 프론트가 받은 Access Token으로 카카오 사용자
 * 정보 API를 직접 호출한다. 이메일은 카카오 앱 설정에서 "이메일" 동의항목을 필수로 설정해둬야 내려온다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoOAuthClient {

  private static final String USER_INFO_URL = "https://kapi.kakao.com/v2/user/me";

  private final RestTemplate restTemplate;

  /** Access Token으로 카카오 사용자 정보 API 호출, 사용자 정보 조회 */
  @SuppressWarnings("unchecked")
  public OAuthUserInfo getUserInfo(String accessToken) {
    // Authorization 헤더에 Access Token을 실어 카카오 사용자 정보 API 호출
    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);
    HttpEntity<Void> request = new HttpEntity<>(headers);

    Map<String, Object> response;
    try {
      ResponseEntity<Map> result =
          restTemplate.exchange(USER_INFO_URL, HttpMethod.GET, request, Map.class);
      response = result.getBody();
    } catch (RestClientException e) {
      log.error("카카오 사용자 정보 조회 실패: {}", e.getMessage());
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    if (response == null || response.get("id") == null) {
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }

    String providerId = String.valueOf(response.get("id"));

    // kakao_account에서 이메일 추출. 동의하지 않았으면 단체 조인에 필요한 이메일(username으로도 씀)이
    // 없어서 인증 실패로 처리한다.
    Map<String, Object> kakaoAccount = (Map<String, Object>) response.get("kakao_account");
    if (kakaoAccount == null || kakaoAccount.get("email") == null) {
      throw new BusinessException(ErrorCode.OAUTH_VERIFICATION_FAILED);
    }
    String email = String.valueOf(kakaoAccount.get("email"));

    // properties에 닉네임이 있으면 이름으로 사용, 없으면 이메일 사용
    String name = email;
    Map<String, Object> properties = (Map<String, Object>) response.get("properties");
    if (properties != null && properties.get("nickname") != null) {
      name = String.valueOf(properties.get("nickname"));
    }

    return new OAuthUserInfo(providerId, email, name);
  }
}
