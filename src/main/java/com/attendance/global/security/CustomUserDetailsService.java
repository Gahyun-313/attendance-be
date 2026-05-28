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

/**
 * Spring Security UserDetailsService 구현체
 *
 * <p>- Spring Security의 인증 프로제스에서 사용자 정보를 로드하는 서비스 - username/userId를 기반으로 데이터베이스에서 사용자 정보를 조회하고
 * UserDetails 객체로 변환해 반환
 *
 * <p>동작 흐름: 1. 사용자가 로그인 요청 (username + password) 2. Spring Security가 loadUserByUsername() 호출 3.
 * 데이터베이스에서 사용자 조회 4. CustomUserDetails 객체로 래핑하여 반환 5. Spring Security가 비밀번호 검증 수행
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  /** 사용자 정보를 조회하기 위한 Repository */
  private final UserRepository userRepository;

  /** username으로 사용자 정보를 로드 - Spring Security의 표준 인증 메커니즘에서 호출되는 메서드 */
  @Override
  public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
    User user =
        userRepository
            .findByUsername(username)
            // @throws UsernameNotFoundException Spring Security 표준 예외 (실제로는 EntityNotFoundException
            // 발생)
            // @throws EntityNotFoundException 사용자가 데이터베이스에 존재하지 않을 때 발생
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    // @return Spring Security가 사용할 UserDetails 객체 (CustomUserDetails로 래핑됨)
    return new CustomUserDetails(user);
  }

  /**
   * 사용자 ID로 UserDetails 로드 - UserDetailSrervice 인터페이스에는 없는 커스텀 메서드
   *
   * <p>JWT 인증 흐름: 1. 클라이언트가 Authorization 헤더에 JWT 토큰 포함하여 요청 2. JwtAuthenticationFilter가 토큰 검증 및
   * 사용자 ID 추출 3. loadUserById(userId) 호출하여 사용자 정보 로드 4. SecurityContext에 인증 정보 설정
   */
  public UserDetails loadUserById(Long userId) {
    User user =
        userRepository
            .findById(userId)
            // @throws EntityNotFoundException 사용자가 데이터베이스에 존재하지 않을 때 발생
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    // @return Spring Security가 사용할 UserDetails 객체 (CustomUserDetails로 래핑됨)
    return new CustomUserDetails(user);
  }
}
