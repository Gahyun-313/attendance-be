package com.attendance.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.attendance.domain.user.dto.ChangePasswordRequest;
import com.attendance.domain.user.dto.CreateUserRequest;
import com.attendance.domain.user.dto.UserResponse;
import com.attendance.domain.user.dto.UserUpdateRequest;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * UserService 단위 테스트
 *
 * Spring Context나 실제 DB를 띄우지 않고,
 * Mockito로 UserRepository/PasswordEncoder를 대체하여 UserService의 비즈니스 로직만 검증한다.
 *
 * 검증하는 주요 정책:
 * - username/email 중복 시 예외 발생, 이후 로직(암호화/저장) 미실행
 * - 회원가입 시 비밀번호 암호화 후 저장, 저장되는 필드 정합성
 * - 기본 사용자 권한은 STUDENT
 * - 현재 비밀번호 불일치 시 비밀번호 변경 차단
 * - 사용자 삭제는 물리 삭제가 아니라 비활성화 처리
 * - 기존 email과 동일한 email 수정 요청 시 중복 체크 생략
 * - 존재하지 않는 사용자에 대한 요청은 USER_NOT_FOUND로 차단
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

  /**
   * @Mock 실제 DB, PasswordEncoder를 사용하지 않고 Mock으로 대체한다.
   * @InjectMocks 선언한 Mock 객체들이 UserService에 자동으로 주입된다.
   */
  @Mock private UserRepository userRepository;
  @Mock private PasswordEncoder passwordEncoder;
  @InjectMocks private UserService userService;

  @Nested
  @DisplayName("createUser()")
  class CreateUser {

    private CreateUserRequest request;

    @Test
    @DisplayName("username이 중복이면 DUPLICATE_USERNAME 예외가 발생한다")
    void duplicateUsername_throwsException() {
      // given
      // 이미 존재하는 username으로 회원가입을 요청하는 상황
      request = new CreateUserRequest("20260001", "password1234", "test@test.com", "홍길동", "A반", null);
      given(userRepository.existsByUsername("20260001")).willReturn(true);

      // when & then
      // username이 중복이면 UserService는 DuplicateException을 던져야 한다
      assertThatThrownBy(() -> userService.createUser(request, 1L))
              .isInstanceOf(DuplicateException.class)
              .extracting(e -> ((DuplicateException) e).getErrorCode())
              .isEqualTo(ErrorCode.DUPLICATE_USERNAME);

      // username 중복에서 바로 막히므로 이후 이메일 검증/암호화/저장이 전부 일어나면 안 됨
      verify(userRepository, never()).existsByEmail(any());
      verify(passwordEncoder, never()).encode(any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("email이 중복이면 DUPLICATE_EMAIL 예외가 발생한다")
    void duplicateEmail_throwsException() {
      // given
      // username은 사용 가능하지만 email은 이미 존재하는 상황
      request = new CreateUserRequest("20260002", "password1234", "dup@test.com", "김철수", "A반", null);
      given(userRepository.existsByUsername("20260002")).willReturn(false);
      given(userRepository.existsByEmail("dup@test.com")).willReturn(true);

      // when & then
      // email이 중복이면 DUPLICATE_EMAIL 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.createUser(request, 1L))
              .isInstanceOf(DuplicateException.class)
              .extracting(e -> ((DuplicateException) e).getErrorCode())
              .isEqualTo(ErrorCode.DUPLICATE_EMAIL);

      // email 중복에서 막히므로 암호화나 저장이 일어나면 안 됨
      verify(passwordEncoder, never()).encode(any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("정상 요청이면 비밀번호를 암호화하여 저장하고 STUDENT로 생성한다")
    void success_encodesPasswordAndSavesAsStudent() {
      // given
      // email은 선택값이므로 null로 요청할 수 있다
      request = new CreateUserRequest("20260003", "password1234", null, "이영희", "B반", null);
      given(userRepository.existsByUsername("20260003")).willReturn(false);
      given(passwordEncoder.encode("password1234")).willReturn("encoded-password");

      // save() 이후 반환될 저장 완료 User
      User savedUser =
              User.builder()
                      .id(1L)
                      .username("20260003")
                      .password("encoded-password")
                      .name("이영희")
                      .groupName("B반")
                      .role(UserRole.STUDENT)
                      .build();
      given(userRepository.save(any(User.class))).willReturn(savedUser);

      // when
      UserResponse response = userService.createUser(request, 1L);

      // then
      // email이 null이면 중복 체크 자체를 하지 않아야 함
      verify(userRepository, never()).existsByEmail(any());
      assertThat(response.getUsername()).isEqualTo("20260003");

      // 비밀번호가 실제로 암호화되어 호출됐는지, save에 전달된 User 필드가 정확한지 확인
      verify(passwordEncoder, times(1)).encode("password1234");
      ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
      verify(userRepository, times(1)).save(userCaptor.capture());
      User capturedUser = userCaptor.getValue();
      assertThat(capturedUser.getUsername()).isEqualTo("20260003");
      assertThat(capturedUser.getPassword()).isEqualTo("encoded-password");
      assertThat(capturedUser.getName()).isEqualTo("이영희");
      assertThat(capturedUser.getGroupName()).isEqualTo("B반");
      assertThat(capturedUser.getRole()).isEqualTo(UserRole.STUDENT);
    }
  }

  @Nested
  @DisplayName("changePassword()")
  class ChangePassword {

    @Test
    @DisplayName("현재 비밀번호가 일치하지 않으면 PASSWORD_MISMATCH 예외가 발생한다")
    void wrongCurrentPassword_throwsException() {
      // given
      // 사용자는 존재하지만 입력한 현재 비밀번호가 저장된 비밀번호와 일치하지 않는 상황
      User user = User.builder().id(1L).password("encoded-old-password").build();
      ChangePasswordRequest request = new ChangePasswordRequest("wrong-password", "new-password123");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(passwordEncoder.matches("wrong-password", "encoded-old-password")).willReturn(false);

      // when & then
      // 현재 비밀번호가 틀리면 PASSWORD_MISMATCH 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.changePassword(1L, request))
              .isInstanceOf(BusinessException.class)
              .extracting(e -> ((BusinessException) e).getErrorCode())
              .isEqualTo(ErrorCode.PASSWORD_MISMATCH);

      // 현재 비밀번호 검증까지는 호출되지만, 불일치 시 새 비밀번호 암호화는 일어나면 안 됨
      verify(passwordEncoder, times(1)).matches("wrong-password", "encoded-old-password");
      verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void userNotFound_throwsException() {
      // given
      // 존재하지 않는 userId로 비밀번호 변경을 요청하는 상황
      ChangePasswordRequest request = new ChangePasswordRequest("old", "new-password123");
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      // 사용자를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.changePassword(999L, request))
              .isInstanceOf(EntityNotFoundException.class)
              .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
              .isEqualTo(ErrorCode.USER_NOT_FOUND);

      // 사용자를 못 찾았으므로 비밀번호 검증/암호화 자체가 일어나면 안 됨
      verify(passwordEncoder, never()).matches(any(), any());
      verify(passwordEncoder, never()).encode(any());
    }

    @Test
    @DisplayName("현재 비밀번호가 일치하면 새 비밀번호로 암호화하여 갱신한다")
    void success_updatesPassword() {
      // given
      // 현재 비밀번호가 정상적으로 일치하는 상황
      User user = User.builder().id(1L).password("encoded-old-password").build();
      ChangePasswordRequest request = new ChangePasswordRequest("old-password", "new-password123");
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(passwordEncoder.matches("old-password", "encoded-old-password")).willReturn(true);
      given(passwordEncoder.encode("new-password123")).willReturn("encoded-new-password");

      // when
      userService.changePassword(1L, request);

      // then
      // 도메인 메서드를 통해 실제 비밀번호 값과 변경 플래그가 갱신되었는지 확인
      assertThat(user.getPassword()).isEqualTo("encoded-new-password");
      assertThat(user.getPasswordChanged()).isTrue();

      // 검증/암호화가 정확히 1번씩 호출되고, 더티 체킹 대상이라 save()는 호출되지 않아야 함
      verify(passwordEncoder, times(1)).matches("old-password", "encoded-old-password");
      verify(passwordEncoder, times(1)).encode("new-password123");
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("deleteUser()")
  class DeleteUser {

    @Test
    @DisplayName("사용자를 삭제하면 물리 삭제 대신 비활성화(active=false) 처리한다")
    void success_deactivatesInsteadOfDeleting() {
      // given
      // 활성 상태의 사용자가 존재하는 상황
      User user = User.builder().id(1L).active(true).build();
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      userService.deleteUser(1L);

      // then
      // 사용자 삭제 정책은 물리 삭제가 아니라 active=false 비활성화 처리
      assertThat(user.getActive()).isFalse();
      verify(userRepository, never()).delete(any());
      verify(userRepository, never()).deleteById(any());
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void userNotFound_throwsException() {
      // given
      // 존재하지 않는 userId로 삭제를 요청하는 상황
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      // 사용자를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.deleteUser(999L))
              .isInstanceOf(EntityNotFoundException.class)
              .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
              .isEqualTo(ErrorCode.USER_NOT_FOUND);

      // 사용자가 없으므로 delete/deleteById/save 어느 것도 호출되면 안 됨
      verify(userRepository, never()).delete(any());
      verify(userRepository, never()).deleteById(any());
      verify(userRepository, never()).save(any());
    }
  }

  @Nested
  @DisplayName("updateUser()")
  class UpdateUser {

    @Test
    @DisplayName("변경하려는 email이 다른 사용자의 email과 중복되면 예외가 발생한다")
    void duplicateEmail_throwsException() {
      // given
      // 기존 email과 다른 email로 변경하려는데, 해당 email이 이미 존재하는 상황
      User user = User.builder().id(1L).email("old@test.com").role(UserRole.STUDENT).build();
      UserUpdateRequest request = new UserUpdateRequest("new@test.com", "홍길동", "A반", null);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));
      given(userRepository.existsByEmail("new@test.com")).willReturn(true);

      // when & then
      // 다른 사용자의 email과 중복되면 DUPLICATE_EMAIL 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.updateUser(1L, request))
              .isInstanceOf(DuplicateException.class)
              .extracting(e -> ((DuplicateException) e).getErrorCode())
              .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("email을 기존과 동일한 값으로 보내면 중복 체크를 하지 않는다")
    void sameEmailAsBefore_skipsDuplicateCheck() {
      // given
      // email은 기존과 동일하게 유지하고 name만 변경하는 상황
      User user = User.builder().id(1L).email("same@test.com").name("old").role(UserRole.STUDENT).build();
      UserUpdateRequest request = new UserUpdateRequest("same@test.com", "new-name", null, null);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      userService.updateUser(1L, request);

      // then
      // 자기 자신의 email이므로 중복 체크를 하지 않고, 이름만 변경되어야 함
      verify(userRepository, never()).existsByEmail(any());
      assertThat(user.getName()).isEqualTo("new-name");
    }

    @Test
    @DisplayName("email이 null이면 중복 체크를 하지 않고 다른 정보만 수정한다")
    void emailNull_skipsDuplicateCheck() {
      // given
      // email은 수정하지 않고 이름과 그룹만 변경하는 상황
      User user =
              User.builder()
                      .id(1L)
                      .email("old@test.com")
                      .name("old-name")
                      .groupName("old-group")
                      .role(UserRole.STUDENT)
                      .build();
      UserUpdateRequest request = new UserUpdateRequest(null, "new-name", "new-group", null);
      given(userRepository.findById(1L)).willReturn(Optional.of(user));

      // when
      userService.updateUser(1L, request);

      // then
      // email이 null이면 중복 체크를 하지 않아야 함
      verify(userRepository, never()).existsByEmail(any());

      // 요청으로 전달된 수정 가능 필드는 정상 변경되어야 함
      assertThat(user.getName()).isEqualTo("new-name");
      assertThat(user.getGroupName()).isEqualTo("new-group");
    }

    @Test
    @DisplayName("존재하지 않는 사용자면 USER_NOT_FOUND 예외가 발생한다")
    void userNotFound_throwsException() {
      // given
      // 존재하지 않는 userId로 사용자 정보 수정을 요청하는 상황
      UserUpdateRequest request = new UserUpdateRequest("test@test.com", "홍길동", "A반", null);
      given(userRepository.findById(999L)).willReturn(Optional.empty());

      // when & then
      // 사용자를 찾을 수 없으면 USER_NOT_FOUND 예외가 발생해야 한다
      assertThatThrownBy(() -> userService.updateUser(999L, request))
              .isInstanceOf(EntityNotFoundException.class)
              .extracting(e -> ((EntityNotFoundException) e).getErrorCode())
              .isEqualTo(ErrorCode.USER_NOT_FOUND);

      // 사용자가 없으므로 email 중복 체크가 일어나면 안 됨
      verify(userRepository, never()).existsByEmail(any());
    }
  }
}