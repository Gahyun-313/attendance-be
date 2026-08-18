package com.attendance.global.security;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Spring Security의 UserDetailsService 구현체. username/userId로 DB에서 사용자를 조회해 UserDetails로 변환. */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UserRepository userRepository;

  /** username으로 사용자 정보 로드. Spring Security 표준 인증 메커니즘에서 호출된다. */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    return new CustomUserDetails(user);
  }

  /** 사용자 ID로 UserDetails 로드. JwtAuthenticationFilter가 토큰에서 추출한 userId로 인증 정보를 채울 때 사용한다. */
  public UserDetails loadUserById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    return new CustomUserDetails(user);
  }
}
