package com.attendance.domain.group.repository;

import com.attendance.domain.group.entity.Group;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** Group 엔티티의 조회/중복 확인 처리 */
public interface GroupRepository extends JpaRepository<Group, Long> {

  /** 단체 내 그룹 전체 목록 조회. 이름순 정렬 */
  List<Group> findByOrganizationIdOrderByName(Long organizationId);

  /** 단체 내 그룹명 존재 여부 확인. 생성 시 중복 체크, User/Session 생성·수정 시 groupName 검증에 사용 */
  boolean existsByOrganizationIdAndName(Long organizationId, String name);

  /** 단체 내 그룹명 존재 여부 확인(자기 자신 제외). 그룹 수정 시 이름 변경 중복 체크에 사용 */
  boolean existsByOrganizationIdAndNameAndIdNot(Long organizationId, String name, Long id);
}
