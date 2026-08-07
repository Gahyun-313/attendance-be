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

/** 사용자 관리 비즈니스 로직 - 학생 계정 생성/조회/수정/비활성화, 비밀번호 변경을 담당 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final AttendanceRepository attendanceRepository;
  private final PasswordEncoder passwordEncoder;

  /**
   * 학생 계정 생성 (ADMIN 전용) POST /api/users - 기존 AuthService.signup()을 이관한 로직 - 관리자가 생성하는 계정은 항상
   * STUDENT (CreateUserRequest.toEntity에서 고정)
   */
  @Transactional
  public UserResponse createUser(CreateUserRequest request, Long organizationId) {
    if (userRepository.existsByUsername(request.getUsername())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_USERNAME);
    }
    if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
    }

    String encodedPassword = passwordEncoder.encode(request.getPassword());
    // organizationId는 생성 요청을 보낸 관리자가 속한 단체로 고정 (요청 바디로 안 받음)
    User user = request.toEntity(encodedPassword, organizationId);
    User savedUser = userRepository.save(user);

    return UserResponse.from(savedUser);
  }

  /**
   * 학생 목록 조회 (ADMIN 전용) GET /api/users - groupName, keyword는 선택 필터 (둘 다 null이면 전체 학생 조회) - ADMIN
   * 계정은 DB에서 직접 관리하므로 STUDENT만 대상으로 조회 - organizationId는 요청한 관리자의 단체로 고정 (다른 단체 학생 노출 방지)
   */
  public Page<UserResponse> getUsers(
      String groupName, String keyword, Long organizationId, Pageable pageable) {
    return userRepository
        .searchStudents(UserRole.STUDENT, organizationId, groupName, keyword, pageable)
        .map(UserResponse::from);
  }

  /**
   * 사용자 상세 조회 (ADMIN 전용) GET /api/users/{userId} - 요청한 관리자와 다른 단체 소속이면 존재 자체를 노출하지 않기 위해 조회
   * 실패(404)로 처리
   */
  public UserResponse getUser(Long userId, Long organizationId) {
    return UserResponse.from(findUserByIdAndOrganization(userId, organizationId));
  }

  /**
   * 존재하는 그룹명 목록 조회 (ADMIN 전용) GET /api/users/groups - 어드민 웹에서 세션 생성 시 그룹 선택 드롭다운 등에 활용 -
   * organizationId는 요청한 관리자의 단체로 고정
   */
  public List<String> getGroups(Long organizationId) {
    return userRepository.findDistinctGroupNames(UserRole.STUDENT, organizationId);
  }

  /**
   * 사용자 대시보드 (ADMIN 전용) GET /api/users/dashboard - 사용자 관리 화면 상단 요약 카드 - 전체/활성 사용자 수, 평균 출석률, 이번 달
   * 신규 대상자 수 - StatisticsService의 overall/dashboard와 동일하게 1분 TTL 캐싱 (organizationId가 캐시 키라서 단체별로
   * 분리됨) - 평균 출석률은 OverallStatisticsResponse와 동일한 공식(전체 집계 방식)을 재사용한다.
   */
  @Cacheable(cacheNames = RedisConfig.CACHE_USER_DASHBOARD)
  public UserDashboardResponse getUserDashboard(Long organizationId) {
    long totalUsers = userRepository.countByRoleAndOrganizationId(UserRole.STUDENT, organizationId);
    long activeUsers =
        userRepository.countByRoleAndOrganizationIdAndActive(
            UserRole.STUDENT, organizationId, true);

    long present =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.PRESENT, organizationId);
    long late =
        attendanceRepository.countByStatusAndOrganizationId(AttendanceStatus.LATE, organizationId);
    long absent =
        attendanceRepository.countByStatusAndOrganizationId(
            AttendanceStatus.ABSENT, organizationId);

    LocalDateTime startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay();
    long newUsersThisMonth =
        userRepository.countByRoleAndOrganizationIdAndCreatedAtBetween(
            UserRole.STUDENT, organizationId, startOfMonth, LocalDateTime.now());

    return UserDashboardResponse.of(
        totalUsers, activeUsers, present, late, absent, newUsersThisMonth);
  }

  /** 내 정보 조회 (본인 전용) GET /api/users/me */
  public UserResponse getMyInfo(Long userId) {
    User user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
    return UserResponse.from(user);
  }

  /**
   * 사용자 정보 수정 (ADMIN 전용) PUT /api/users/{userId} - email 변경 시 중복 검사 수행 - username, password, role은
   * 수정 대상 아님
   */
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

  /**
   * 사용자 삭제 (ADMIN 전용) DELETE /api/users/{userId} - 물리 삭제가 아닌 비활성화(soft delete) 처리 - 출석
   * 기록(AttendanceRecord) 등 연관 데이터 보존을 위해 실제 삭제는 하지 않음
   */
  @Transactional
  public void deleteUser(Long userId, Long organizationId) {
    User user = findUserByIdAndOrganization(userId, organizationId);
    user.deactivate();
  }

  /**
   * 사용자 재활성화 (ADMIN 전용) POST /api/users/{userId}/active - deleteUser로 비활성화 된 사용자를 다시 활성 상태로 되돌린다.
   */
  @Transactional
  public UserResponse activateUser(Long userID, Long organizationId) {
    User user = findUserByIdAndOrganization(userID, organizationId);
    user.activate();
    return UserResponse.from(user);
  }

  /**
   * 비밀번호 변경 (본인 전용) PATCH /api/users/me/password - 현재 비밀번호 확인 후 새 비밀번호로 변경 - 변경 성공 시
   * passwordChanged=true로 갱신 (User.changePassword 내부 처리)
   */
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

  /** 사용자 조회 + 소속 단체 검증 공통 메서드 - 다른 단체 소속 userId를 조작(수정/삭제)하려는 시도를 존재 자체가 없는 것처럼(404) 차단한다. */
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
