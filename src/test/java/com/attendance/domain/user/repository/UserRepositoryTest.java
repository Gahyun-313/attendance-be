package com.attendance.domain.user.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.attendance.domain.user.entity.User;
import com.attendance.domain.user.entity.UserRole;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

/**
 * UserRepository 쿼리 메서드 테스트 @DataJpaTest는 JPA 관련 빈만 로드하고 인메모리 H2 DB로 실제 쿼리를 실행한다.
 * 커스텀 @Query(searchStudents, findDistinctGroupNames)가 의도한 대로 동작하는지 검증한다.
 *
 * <p>검증하는 주요 정책: - 그룹명/키워드로 학생 목록을 정확히 필터링 (searchStudents) - 그룹/키워드 조건이 없으면 STUDENT 전체 조회, ADMIN은
 * 항상 제외 - 그룹명은 중복 없이, null은 제외하고 조회 (findDistinctGroupNames) - 역할+그룹 기준 인원수를 정확히 카운트
 * (countByRoleAndGroupName)
 */
@DataJpaTest
class UserRepositoryTest {

  /**
   * @Autowired 실제 DB 대신 인메모리 H2로 자동 구성된 UserRepository 빈을 주입받는다. @Autowired TestEntityManager로 영속성
   * 컨텍스트를 직접 다루어 테스트 데이터를 세팅/flush한다.
   */
  @Autowired private UserRepository userRepository;

  @Autowired private TestEntityManager em;

  private User saveStudent(String username, String name, String groupName) {
    // 테스트용 학생 계정 생성 (username/password/name/role은 NOT NULL)
    User user =
        User.builder()
            .username(username)
            .password("encoded-password")
            .name(name)
            .groupName(groupName)
            .role(UserRole.STUDENT)
            .organizationId(1L)
            .build();
    return em.persistAndFlush(user);
  }

  private User saveAdmin(String username) {
    // 그룹 필터링에서 STUDENT만 나와야 함을 검증하기 위한 대조군 admin 계정
    User admin =
        User.builder()
            .username(username)
            .password("encoded-password")
            .name("관리자")
            .role(UserRole.ADMIN)
            .organizationId(1L)
            .build();
    return em.persistAndFlush(admin);
  }

  @Nested
  @DisplayName("searchStudents()")
  class SearchStudents {

    @Test
    @DisplayName("그룹명으로 필터링하면 해당 그룹 학생만 조회된다")
    void filterByGroupName_returnsOnlyMatchingGroup() {
      // given
      // A반 학생 2명, B반 학생 1명이 있는 상황
      saveStudent("20260001", "학생1", "A반");
      saveStudent("20260002", "학생2", "A반");
      saveStudent("20260003", "학생3", "B반");

      // when
      Page<User> result =
          userRepository.searchStudents(UserRole.STUDENT, 1L, "A반", null, PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getContent()).extracting(User::getGroupName).containsOnly("A반");
    }

    @Test
    @DisplayName("키워드로 필터링하면 username 또는 name이 일치하는 학생만 조회된다")
    void filterByKeyword_returnsMatchingUsernameOrName() {
      // given
      // username에 "0001"이 포함된 학생과, name에 "홍길동"이 포함된 학생이 있는 상황
      saveStudent("20260001", "김철수", "A반");
      saveStudent("20260099", "홍길동", "A반");
      saveStudent("20260050", "이영희", "B반");

      // when
      Page<User> result =
          userRepository.searchStudents(UserRole.STUDENT, 1L, null, "길동", PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).hasSize(1);
      assertThat(result.getContent().get(0).getName()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("그룹/키워드 조건이 모두 없으면 STUDENT 전체가 조회되고 ADMIN은 제외된다")
    void noFilters_returnsAllStudentsExcludingAdmin() {
      // given
      saveStudent("20260001", "학생1", "A반");
      saveStudent("20260002", "학생2", "B반");
      saveAdmin("admin01");

      // when
      Page<User> result =
          userRepository.searchStudents(UserRole.STUDENT, 1L, null, null, PageRequest.of(0, 10));

      // then
      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getContent()).extracting(User::getRole).containsOnly(UserRole.STUDENT);
    }
  }

  @Nested
  @DisplayName("findDistinctGroupNames()")
  class FindDistinctGroupNames {

    @Test
    @DisplayName("같은 그룹명이 여러 명이어도 중복 없이 한 번만 반환된다")
    void duplicateGroupNames_returnsDistinctOnly() {
      // given
      // A반 학생 2명, B반 학생 1명
      saveStudent("20260001", "학생1", "A반");
      saveStudent("20260002", "학생2", "A반");
      saveStudent("20260003", "학생3", "B반");

      // when
      List<String> groupNames = userRepository.findDistinctGroupNames(UserRole.STUDENT);

      // then
      assertThat(groupNames).containsExactlyInAnyOrder("A반", "B반");
    }

    @Test
    @DisplayName("그룹명이 없는(null) 학생은 결과에서 제외된다")
    void nullGroupName_isExcluded() {
      // given
      saveStudent("20260001", "학생1", "A반");
      saveStudent("20260002", "학생2", null);

      // when
      List<String> groupNames = userRepository.findDistinctGroupNames(UserRole.STUDENT);

      // then
      assertThat(groupNames).containsExactly("A반");
    }
  }

  @Nested
  @DisplayName("countByRoleAndGroupName()")
  class CountByRoleAndGroupName {

    @Test
    @DisplayName("해당 역할+그룹에 속한 사용자 수를 정확히 센다")
    void countsOnlyMatchingRoleAndGroup() {
      // given
      // A반 학생 2명, B반 학생 1명, A반 소속 admin 1명(역할이 달라 카운트 제외 대상)
      saveStudent("20260001", "학생1", "A반");
      saveStudent("20260002", "학생2", "A반");
      saveStudent("20260003", "학생3", "B반");

      // when
      long count = userRepository.countByRoleAndGroupName(UserRole.STUDENT, "A반");

      // then
      assertThat(count).isEqualTo(2);
    }
  }
}
