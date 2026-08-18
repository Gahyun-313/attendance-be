package com.attendance.domain.user.service;

import com.attendance.domain.attendance.entity.AttendanceStatus;
import com.attendance.domain.attendance.repository.AttendanceRepository;
import com.attendance.domain.user.dto.*;
import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.config.RedisConfig;
import com.attendance.global.exception.BusinessException;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 사용자 관리 비즈니스 로직 처리. 학생 계정 생성/조회/수정/비활성화, 비밀번호 변경 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final AttendanceRepository attendanceRepository;
  private final PasswordEncoder passwordEncoder;

  /** 학생 계정 생성(ADMIN). 관리자가 만드는 계정은 항상 STUDENT로 고정한다. */
  @Transactional
  public UserResponse createUser(CreateUserRequest request, Long organizationId) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_USERNAME);
    }
    if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    String encodedPassword = passwordEncoder.encode(request.getPassword());
    // organizationId는 요청 바디로 받지 않고, 생성 요청을 보낸 관리자가 속한 단체로 고정한다.
    User user = request.toEntity(encodedPassword, organizationId);
    User savedUser = userRepository.save(user);

    return UserResponse.from(savedUser);
  }

  /** 학생 목록 조회(ADMIN). groupName과 keyword는 둘 다 선택 필터이며, organizationId로 단체 격리 */
  public Page<UserResponse> getUsers(
      String groupName, String keyword, Long organizationId, Pageable pageable) {
    return userRepository
        .searchStudents(UserRole.STUDENT, organizationId, groupName, keyword, pageable)
        .map(UserResponse::from);
  }

  /** 사용자 상세 조회(ADMIN). 다른 단체 소속이면 404로 존재 자체를 숨긴다. */
  public UserResponse getUser(Long userId, Long organizationId) {
    return UserResponse.from(findUserByIdAndOrganization(userId, organizationId));
  }

  /** 존재하는 그룹명 목록 조회(ADMIN). 세션 생성 시 그룹 선택 드롭다운 등에 사용한다. */
  public List<String> getGroups(Long organizationId) {
    return userRepository.findDistinctGroupNames(UserRole.STUDENT, organizationId);
  }

  /**
   * 사용자 대시보드 조회(ADMIN) 전체/활성 사용자 수, 상태별 출석 건수, 이번 달 신규 대상자 수 집계 StatisticsService와 동일하게
   * organizationId를 캐시 키로 1분간 캐싱해 단체별로 분리한다.
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_USER_DASHBOARD)
  public UserDashboardResponse getUserDashboard(Long organizationId) {
    // 전체 사용자 수, 활성 사용자 수 조회
    long totalUsers = userRepository.countByRoleAndOrganizationId(UserRole.STUDENT, organizationId);
    long activeUsers =
        userRepository.countByRoleAndOrganizationIdAndActive(
            UserRole.STUDENT, organizationId, true);

    // 상태별 출석 건수 조회
    long present =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.PRESENT, organizationId);
    long late =
        attendanceRepository.countByStatusAndOrganizationId(AttendanceStatus.LATE, organizationId);
    long absent =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.ABSENT, organizationId);

    // 이번 달 1일부터 현재까지 생성된 신규 사용자 수 조회
    LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    long newUsersThisMonth =
        userRepository.countByRoleAndOrganizationIdAndCreatedAtBetween(
            UserRole.STUDENT, organizationId, startOfMonth, LocalDateTime.now());

    return UserDashboardResponse.of(
        totalUsers, activeUsers, present, late, absent, newUsersThisMonth);
  }

  /** 내 정보 조회(본인 전용) */
  public UserResponse getMyInfo(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  /** 사용자 정보 수정(ADMIN). email 변경 시에만 중복을 검사하며, username/password/role은 수정 대상이 아니다. */
  @Transactional
  public UserResponse updateUser(Long userId, UserUpdateRequest request, Long organizationId) {
    User user = findUserByIdAndOrganization(userId, organizationId);

    if (request.getEmail() != null
        && !request.getEmail().equals(user.getEmail())
        && userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    user.updateInfo(
        request.getName(), request.getEmail(), request.getGroupName(), request.getNote());
    return UserResponse.from(user);
  }

  /** 사용자 삭제(ADMIN). 출석 기록 등 연관 데이터를 보존하기 위해 물리 삭제 대신 비활성화(soft delete)한다. */
  @Transactional
  public void deleteUser(Long userId, Long organizationId) {
    User user = findUserByIdAndOrganization(userId, organizationId);
    user.deactivate();
  }

  /** 사용자 재활성화(ADMIN). deleteUser로 비활성화된 사용자를 다시 활성 상태로 복구 */
  @Transactional
  public UserResponse activateUser(Long userID, Long organizationId) {
    User user = findUserByIdAndOrganization(userID, organizationId);
    user.activate();
    return UserResponse.from(user);
  }

  /** 비밀번호 변경(본인). 현재 비밀번호를 확인한 뒤 새 비밀번호로 교체 */
  @Transactional
  public void changePassword(Long userId, ChangePasswordRequest request) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

    if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
      throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
    }

    String encodedPassword = passwordEncoder.encode(request.getNewPassword());
    user.changePassword(encodedPassword);
  }

  /** 사용자를 조회하고 소속 단체 검증. 다른 단체 소속 userId를 조작하려는 시도는 존재 자체가 없는 것처럼 404로 차단한다. */
  private User findUserByIdAndOrganization(Long userId, Long organizationId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    if (!user.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.USER_NOT_FOUND);
    }
    return user;
  }
}
