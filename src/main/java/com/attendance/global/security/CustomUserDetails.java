package com.attendance.global.security;

import com.attendance.domain.user.entity.User;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.Collections;

/**
 * Spring Security UserDetails 구현체
 * - Spring Security의 인증/인가 처리를 위해 사용자 정보를 제공하는 클래스
 * - User 엔티티를 래핑하여 Spring Security가 요구하는 UserDetails 인터페이스를 구현
 */
@Getter
@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    /** 실제 사용자 정보를 담고 있는 User 엔티티 */
    private final User user;

    /** 사용자의 권한 목록을 반환 */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(
                // User 엔티티의 role에 "ROLE_" 접두사를 붙여 Spring Security의 권한 형식으로 반환
                new SimpleGrantedAuthority("ROLE_" + user.getRole().name())
        );
    }

    /** 사용자의 암호화된 비밀번호를 반환 */
    @Override
    public String getPassword() {
        return user.getPassword();
    }

    /** 사용자의 로그인 아이디(username)를 반환 */
    @Override
    public String getUsername() {
        return user.getUsername();
    }

    /** 계정 만료 여부를 확인 */
    @Override
    public boolean isAccountNonExpired() {
        // 현재는 사용하지 않음 (항상 true)
        return true;
    }

    /** 계정 잠김 여부를 확인 */
    @Override
    public boolean isAccountNonLocked() {
        // 현재는 사용하지 않음 (항상 true)
        return true;
    }

    /** 비밀번호 만료 여부를 확인 */
    @Override
    public boolean isCredentialsNonExpired() {
        // 현재는 사용하지 않음 (항상 true)
        return true;
    }

    /** 계정 활성화 여부를 확인 */
    @Override
    public boolean isEnabled() {
        // 현재는 사용하지 않음 (항상 true)
        return true;
    }

    /** 사용자의 고유 ID를 반환
     * - UserDetails에는 없는 커스텀 메서드로, 비즈니스 로직에서 사용자 ID가 필요할 때 사용
     */
    public Long getUserId() {
        return user.getId();
    }

    /** 사용자의 역할(권한) 이름을 반환
     * - UserDetails에는 없는 커스텀 메서드로, 역할 이름이 필요할 때 사용
     */
    public String getRole() {
        return user.getRole().name();
    }

}
