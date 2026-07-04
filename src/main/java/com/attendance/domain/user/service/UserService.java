package com.attendance.domain.user.service;

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
import lombok.RequiredArgsConstructor;
import java.util.List;
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
    private final PasswordEncoder passwordEncoder;

    /**
     * 학생 계정 생성 (ADMIN 전용) POST /api/users - 기존 AuthService.signup()을 이관한 로직 - 관리자가 생성하는 계정은 항상
     * STUDENT (CreateUserRequest.toEntity에서 고정)
     */
    @Transactional
    public UserResponse createUser(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_USERNAME);
        }
        if (request.getEmail() != null && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
        }

        String encodedPassword = passwordEncoder.encode(request.getPassword());
        User user = request.toEntity(encodedPassword);
        User savedUser = userRepository.save(user);

        return UserResponse.from(savedUser);
    }

    /**
     * 학생 목록 조회 (ADMIN 전용) GET /api/users - groupName, keyword는 선택 필터 (둘 다 null이면 전체 학생 조회) -
     * ADMIN 계정은 DB에서 직접 관리하므로 STUDENT만 대상으로 조회
     */
    public Page<UserResponse> getUsers(String groupName, String keyword, Pageable pageable) {
        return userRepository
                .searchStudents(UserRole.STUDENT, groupName, keyword, pageable)
                .map(UserResponse::from);
    }

    /** 사용자 상세 조회 (ADMIN 전용) GET /api/users/{userId} */
    public UserResponse getUser(Long userId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * 존재하는 그룹명 목록 조회 (ADMIN 전용) GET /api/users/groups - 어드민 웹에서 세션 생성 시 그룹 선택 드롭다운 등에 활용
     */
    public List<String> getGroups() {
        return userRepository.findDistinctGroupNames(UserRole.STUDENT);
    }

    /** 내 정보 조회 (본인 전용) GET /api/users/me */
    public UserResponse getMyInfo(Long userId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        return UserResponse.from(user);
    }

    /**
     * 사용자 정보 수정 (ADMIN 전용) PUT /api/users/{userId} - email 변경 시 중복 검사 수행 - username, password,
     * role은 수정 대상 아님
     */
    @Transactional
    public UserResponse updateUser(Long userId, UserUpdateRequest request) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (request.getEmail() != null
                && !request.getEmail().equals(user.getEmail())
                && userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateException(ErrorCode.DUPLICATE_EMAIL);
        }

        user.updateInfo(request.getName(), request.getEmail(), request.getGroupName(), request.getNote());
        return UserResponse.from(user);
    }

    /**
     * 사용자 삭제 (ADMIN 전용) DELETE /api/users/{userId} - 물리 삭제가 아닌 비활성화(soft delete) 처리 - 출석
     * 기록(AttendanceRecord) 등 연관 데이터 보존을 위해 실제 삭제는 하지 않음
     */
    @Transactional
    public void deleteUser(Long userId) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));
        user.deactivate();
    }

    /**
     * 비밀번호 변경 (본인 전용) PATCH /api/users/me/password - 현재 비밀번호 확인 후 새 비밀번호로 변경 - 변경 성공 시
     * passwordChanged=true로 갱신 (User.changePassword 내부 처리)
     */
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user =
                userRepository.findById(userId).orElseThrow(() -> new EntityNotFoundException(ErrorCode.USER_NOT_FOUND));

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_MISMATCH);
        }

        String encodedPassword = passwordEncoder.encode(request.getNewPassword());
        user.changePassword(encodedPassword);
    }
}