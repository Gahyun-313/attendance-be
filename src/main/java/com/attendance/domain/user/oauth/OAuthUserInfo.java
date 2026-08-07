package com.attendance.domain.user.oauth;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 소셜 로그인 제공자(구글/카카오)로부터 받아온 사용자 식별 정보 - providerId: 제공자가 부여한 고유 사용자 식별자 (구글 sub, 카카오 id) - email:
 * 신규 조인 시 username으로도 사용 (단체 내 유니크해야 하므로 이메일 사용)
 */
@Getter
@AllArgsConstructor
public class OAuthUserInfo {
  private String providerId;
  private String email;
  private String name;
}
