package com.attendance.domain.user.repository;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * User Repository : User 엔티티에 대한 데이터 접근 계층
 *
 * <p>- 사용자 정보의 조회, 검색, 중복 확인 등의 데이터베이스 작업을 처리 - Spring Data JPA를 사용하여 기본적인 CRUD 작업과 커스텀 쿼리를 제공
 */
public interface UserRepository extends JpaRepository<User, Long> {

  /** username으로 사용자 조회 */
  Optional<User> findByUsername(String username);

  /** email로 사용자 조회 */
  Optional<User> findByEmail(String email);

  /** username 존재 여부 확인 */
  boolean existsByUsername(String username);

  /** email 존재 여부 확인 */
  boolean existsByEmail(String email);

  /** 특정 Role을 가진 사용자 목록을 페이징하여 조회 (페이징) */
  Page<User> findByRole(UserRole role, Pageable pageable);

  /** 이름에 특정 문자열이 포함된 사용자를 검색 (페이징) - 부분일치 검색을 지원, 대소문지 구분 */
  Page<User> findByNameContaining(String name, Pageable pageable);

  /**
   * username 또는 name에서 키워드를 검색 (페이징) - 두 필드를 동시에 검색하여 더 넓은 범위의 검색 결과를 제공 - LIKE 연산자를 사용해 부분 일치 검색을
   * 수행
   *
   * <p>사용 예시: - keyword = "kim" → username="kim123" 또는 name="김철수" 모두 검색
   *
   * @param keyword 검색할 키워드 (username 또는 name에서 검색)
   * @param pageable 페이징 및 정렬 정보
   * @return 검색 조건에 맞는 사용자 목록의 페이지 객체
   */
  @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.name LIKE %:keyword%")
  Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

  /**
   * 특정 Role을 가진 사용자 중에서 키워드로 검색 (페이징) - 역할 필터링과 키워드 검색을 동시에 적용
   *
   * <p>사용 예시: - role = UserRole.STUDENT, keyword = "김" → 학생 중에서 "김"이 포함된 사용자 검색
   *
   * @param role 필터링할 사용자 역할
   * @param keyword 검색할 키워드 (username 또는 name에서 검색)
   * @param pageable 페이징 및 정렬 정보
   * @return 역할과 검색 조건에 맞는 사용자 목록의 페이지 객체
   */
  @Query(
      "SELECT u FROM User u WHERE u.role = :role AND (u.username LIKE %:keyword% OR u.name LIKE %:keyword%)")
  Page<User> searchByRoleAndKeyword(
      @Param("role") UserRole role, @Param("keyword") String keyword, Pageable pageable);

  /**
   * 학생 목록 조회 (그룹/이름·학번 검색, 페이징) - GET /api/users (ADMIN)에서 사용 - groupName, keyword는 선택값이며 null이면 해당
   * 조건 미적용 - ADMIN 계정은 DB에서 직접 관리하므로 role=STUDENT로 고정 조회
   *
   * @param role 조회할 사용자 역할 (STUDENT 고정)
   * @param groupName 그룹명 필터 (선택, null이면 전체)
   * @param keyword username/name 검색 키워드 (선택, null이면 전체)
   * @param pageable 페이징 및 정렬 정보
   * @return 조건에 맞는 사용자 목록의 페이지 객체
   */
  @Query(
      "SELECT u FROM User u WHERE u.role = :role "
          + "AND (:groupName IS NULL OR u.groupName = :groupName) "
          + "AND (:keyword IS NULL OR u.username LIKE %:keyword% OR u.name LIKE %:keyword%)")
  Page<User> searchStudents(
      @Param("role") UserRole role,
      @Param("groupName") String groupName,
      @Param("keyword") String keyword,
      Pageable pageable);

  /**
   * 존재하는 그룹명 목록 조회 (distinct) - GET /api/users/groups (ADMIN)에서 사용 - 어드민 웹에서 세션 생성 시 그룹 선택 드롭다운 등에
   * 활용 - null 그룹명은 제외, 그룹명 오름차순 정렬
   *
   * @param role 조회할 사용자 역할 (STUDENT 고정)
   * @return 중복 제거된 그룹명 목록
   */
  @Query(
      "SELECT DISTINCT u.groupName FROM User u "
          + "WHERE u.role = :role AND u.groupName IS NOT NULL ORDER BY u.groupName")
  List<String> findDistinctGroupNames(@Param("role") UserRole role);

  /**
   * 특정 역할 + 그룹명에 속한 사용자 전체 조회 (페이징 없음) - 세션 시작 시 대상 그룹 학생 전원에게 WAITING 레코드를 사전 생성할 때 사용
   * (AttendanceService.initializeWaitingRecords)
   */
  List<User> findByRoleAndGroupName(UserRole role, String groupName);

  /**
   * 특정 역할 + 그룹명에 속한 사용자 수 - 출석 대시보드의 "대상자 수"(targetCount) 산출에 사용
   * (AttendanceService.getSessionDashboard)
   */
  long countByRoleAndGroupName(UserRole role, String groupName);

  /** 역할별 전체 사용자 수 - 전체 통계(overall)의 전체 학생 수 집계용 */
  long countByRole(UserRole role);

  /**
   * 특정 역할에 속한 사용자 전체 조회 (페이징 없음) - 알림 전체발송(targetGroup 미지정) 시 대상자 조회에 사용
   * (NotificationService.dispatch)
   */
  List<User> findByRole(UserRole role);
}
