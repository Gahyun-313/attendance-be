package com.attendance.global.security;

import com.attendance.domain.user.entity.User;
import java.util.Collection;
import java.util.Collections;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** Spring Security의 UserDetails 구현체. User 엔티티를 감싸서 인증/인가에 필요한 정보 제공. */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

  private final User user;

  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()));
  }

  @Override
  public String getPassword() {
    return user.getPassword();
  }

  @Override
  public String getUsername() {
    return user.getUsername();
  }

  @Override
  public boolean isAccountNonExpired() {
    return true; // 현재 미사용
  }

  @Override
  public boolean isAccountNonLocked() {
    return true; // 현재 미사용
  }

  @Override
  public boolean isCredentialsNonExpired() {
    return true; // 현재 미사용
  }

  @Override
  public boolean isEnabled() {
    return true; // 현재 미사용
  }

  /** UserDetails에는 없는 커스텀 필드. 비즈니스 로직에서 사용자 ID가 필요할 때 사용한다. */
  public Long getUserId() {
    return user.getId();
  }

  /** UserDetails에는 없는 커스텀 필드. 역할 이름이 필요할 때 사용한다. */
  public String getRole() {
    return user.getRole().name();
  }

  /**
   * 소속 단체 ID 반환
   * 매 요청마다 CustomUserDetailsService가 User를 DB에서 다시 조회해 담기 때문에
   * JWT 클레임에 따로 넣지 않아도 항상 최신 값을 얻는다. 사용자/세션 생성 시 organizationId가 필요한 곳에서 사용한다.
   */
  public Long getOrganizationId() {
    return user.getOrganizationId();
  }
}
