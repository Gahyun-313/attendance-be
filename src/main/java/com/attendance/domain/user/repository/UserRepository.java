package com.attendance.domain.user.repository;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** User 엔티티의 조회/검색/중복 확인 처리 */
public interface UserRepository extends JpaRepository<User, Long> {

  /** username으로 사용자 조회 */
  Optional<User> findByUsername(String username);

  /** email로 사용자 조회 */
  Optional<User> findByEmail(String email);

  /** provider와 providerId로 사용자 조회. 소셜 계정이 이미 연결됐는지 확인할 때 사용. */
  Optional<User> findByProviderAndProviderId(String provider, String providerId);

  /** username 존재 여부 확인 */
  boolean existsByUsername(String username);

  /** email 존재 여부 확인 */
  boolean existsByEmail(String email);

  /** 특정 Role의 사용자 목록 페이징 조회 */
  Page<User> findByRole(UserRole role, Pageable pageable);

  /** 이름에 특정 문자열이 포함된 사용자 부분일치 검색 */
  Page<User> findByNameContaining(String name, Pageable pageable);

  /** username 또는 name에 키워드가 포함된 사용자 검색 */
  @Query("SELECT u FROM User u WHERE u.username LIKE %:keyword% OR u.name LIKE %:keyword%")
  Page<User> searchByKeyword(@Param("keyword") String keyword, Pageable pageable);

  /** Role과 키워드로 사용자 검색 */
  @Query(
      "SELECT u FROM User u WHERE u.role = :role AND (u.username LIKE %:keyword% OR u.name LIKE %:keyword%)")
  Page<User> searchByRoleAndKeyword(
      @Param("role") UserRole role, @Param("keyword") String keyword, Pageable pageable);

  /**
   * 그룹/키워드로 학생 목록 검색(ADMIN).
   * groupName과 keyword는 선택값이라 null이면 조건에서 제외하고, organizationId로 단체를 격리한다.
   */
  @Query(
      "SELECT u FROM User u WHERE u.role = :role AND u.organizationId = :organizationId "
          + "AND (:groupName IS NULL OR u.groupName = :groupName) "
          + "AND (:keyword IS NULL OR u.username LIKE %:keyword% OR u.name LIKE %:keyword%)")
  Page<User> searchStudents(
      @Param("role") UserRole role,
      @Param("organizationId") Long organizationId,
      @Param("groupName") String groupName,
      @Param("keyword") String keyword,
      Pageable pageable);

  /** 존재하는 그룹명 목록 중복 없이 조회 */
  @Query(
      "SELECT DISTINCT u.groupName FROM User u "
          + "WHERE u.role = :role AND u.groupName IS NOT NULL ORDER BY u.groupName")
  List<String> findDistinctGroupNames(@Param("role") UserRole role);

  /** 단체 범위로 한정해 그룹명 목록 조회. findDistinctGroupNames(role)의 단체 격리 버전. */
  @Query(
      "SELECT DISTINCT u.groupName FROM User u "
          + "WHERE u.role = :role AND u.organizationId = :organizationId AND u.groupName IS NOT NULL "
          + "ORDER BY u.groupName")
  List<String> findDistinctGroupNames(
      @Param("role") UserRole role, @Param("organizationId") Long organizationId);

  /** 역할+그룹명으로 사용자 전체 조회. 세션 시작 시 WAITING 레코드를 미리 생성할 때 사용. */
  List<User> findByRoleAndGroupName(UserRole role, String groupName);

  /** 역할+그룹명 사용자 수 조회. 출석 대시보드의 대상자 수(targetCount) 산출에 사용. */
  long countByRoleAndGroupName(UserRole role, String groupName);

  /** 역할별 전체 사용자 수 조회. 전체 통계 집계에 사용. */
  long countByRole(UserRole role);

  /** 역할+단체별 전체 사용자 수 조회. countByRole의 단체 격리 버전. */
  long countByRoleAndOrganizationId(UserRole role, Long organizationId);

  /** 역할+단체+활성여부별 사용자 수 조회. 사용자 대시보드의 활성 사용자 수 집계에 사용. */
  long countByRoleAndOrganizationIdAndActive(UserRole role, Long organizationId, boolean activate);

  /** 역할+단체+생성일시 구간별 사용자 수 조회. 이번 달 신규 대상자 수 집계에 사용. */
  long countByRoleAndOrganizationIdAndCreatedAtBetween(
      UserRole role, Long organizationId, LocalDateTime start, LocalDateTime end);

  /** 역할+단체별 사용자 전체 조회. 출석률 랭킹 집계에서 순회용으로 사용. */
  List<User> findByRoleAndOrganizationId(UserRole role, Long organizationId);

  /** 역할+그룹명+단체별 사용자 수 조회. 그룹별 출석률 집계를 단체 범위로 한정할 때 사용. */
  long countByRoleAndGroupNameAndOrganizationId(
      UserRole role, String groupName, Long organizationId);

  /** 특정 역할의 사용자 전체 조회. 알림 전체발송 시 대상자 조회에 사용. */
  List<User> findByRole(UserRole role);
}
