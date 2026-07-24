package com.attendance.domain.organization.repository;

import com.attendance.domain.organization.entity.Organization;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 단체(Organization) Repository */
public interface OrganizationRepository extends JpaRepository<Organization, Long> {

  /** 초대 코드로 단체 조회 - 소셜 로그인으로 신규 어드민이 조인할 때 코드 검증에 사용 */
  Optional<Organization> findByCode(String code);

  /** 초대 코드 존재 여부 확인 - 코드 발급/재발급 시 중복 체크에 사용 */
  boolean existsByCode(String code);
}
