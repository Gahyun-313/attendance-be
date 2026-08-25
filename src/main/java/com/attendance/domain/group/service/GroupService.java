package com.attendance.domain.group.service;

import com.attendance.domain.group.dto.GroupRequest;
import com.attendance.domain.group.dto.GroupResponse;
import com.attendance.domain.group.entity.Group;
import com.attendance.domain.group.repository.GroupRepository;
import com.attendance.domain.session.repository.SessionRepository;
import com.attendance.domain.user.repository.UserRepository;
import com.attendance.global.exception.DuplicateException;
import com.attendance.global.exception.EntityNotFoundException;
import com.attendance.global.exception.ErrorCode;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 그룹 마스터 비즈니스 로직 처리 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

  private final GroupRepository groupRepository;
  private final UserRepository userRepository;
  private final SessionRepository sessionRepository;

  /** 그룹 생성. organizationId는 요청을 보낸 관리자의 단체로 고정한다. */
  @Transactional
  public GroupResponse createGroup(GroupRequest request, Long organizationId) {
    if (groupRepository.existsByOrganizationIdAndName(organizationId, request.getName())) {
      throw new DuplicateException(ErrorCode.DUPLICATE_GROUP_NAME);
    }
    Group savedGroup = groupRepository.save(request.toEntity(organizationId));
    return GroupResponse.from(savedGroup);
  }

  /** 단체 내 그룹 전체 목록 조회 */
  public List<GroupResponse> getGroups(Long organizationId) {
    return groupRepository.findByOrganizationIdOrderByName(organizationId).stream()
        .map(GroupResponse::from)
        .toList();
  }

  /** 그룹 수정. 이름이 바뀌면 이 그룹을 참조하던 User/Session의 문자열 groupName도 함께 동기화한다. */
  @Transactional
  public GroupResponse updateGroup(Long groupId, GroupRequest request, Long organizationId) {
    Group group = findGroupByIdAndOrganization(groupId, organizationId);

    String oldName = group.getName();
    boolean nameChanged = !oldName.equals(request.getName());
    if (nameChanged
        && groupRepository.existsByOrganizationIdAndNameAndIdNot(
            organizationId, request.getName(), groupId)) {
      throw new DuplicateException(ErrorCode.DUPLICATE_GROUP_NAME);
    }

    group.updateInfo(request.getName(), request.getDescription());

    // 그룹명이 바뀌면 이 이름을 참조하던 User/Session의 문자열도 같이 바꿔야 참조가 끊기지 않는다.
    if (nameChanged) {
      userRepository.renameGroupName(organizationId, oldName, request.getName());
      sessionRepository.renameGroupName(organizationId, oldName, request.getName());
    }

    return GroupResponse.from(group);
  }

  /** 그룹 삭제. 소속 사용자는 막지 않고 User/Session의 groupName만 비운(null) 뒤 그룹을 삭제한다. */
  @Transactional
  public void deleteGroup(Long groupId, Long organizationId) {
    Group group = findGroupByIdAndOrganization(groupId, organizationId);
    userRepository.clearGroupName(organizationId, group.getName());
    sessionRepository.clearGroupName(organizationId, group.getName());
    groupRepository.delete(group);
  }

  /** ID와 organizationId로 그룹을 조회한다. 다른 단체 소속이면 404로 존재 자체를 숨긴다(다른 도메인과 동일 패턴). */
  private Group findGroupByIdAndOrganization(Long groupId, Long organizationId) {
    Group group =
        groupRepository
            .findById(groupId)
            .orElseThrow(() -> new EntityNotFoundException(ErrorCode.GROUP_NOT_FOUND));
    if (!group.getOrganizationId().equals(organizationId)) {
      throw new EntityNotFoundException(ErrorCode.GROUP_NOT_FOUND);
    }
    return group;
  }
}
