package com.attendance.domain.user.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/** 사용자 엔티티 Spring Security의 UserDetails를 구현하여 인증 시스템과 통합 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  // 로그인에 사용되는 고유 아이디
  @Column(nullable = false, unique = true, length = 50)
  private String username;

  // 암호화된 비밀번호 - 소셜 로그인(provider != null) 계정은 비밀번호가 없어 null 가능
  @Column private String password;

  // 실제 이름
  @Column(nullable = false, length = 100)
  private String name;

  // 이메일 (선택)
  @Column(unique = true, length = 100)
  private String email;

  // 전화번호 (선택)
  @Column(length = 20)
  private String phone;

  // 사용자 역할 (STUDENT/ADMIN)
  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private UserRole role;

  // 소속 단체 (Organization FK) - 연관관계 대신 Long으로 저장 (AttendanceRecord.userId와 동일 패턴)
  @Column(name = "organization_id", nullable = false)
  private Long organizationId;

  // 소셜 로그인 제공자 (GOOGLE/KAKAO 등) - 비밀번호 로그인 계정은 null
  @Column(length = 20)
  private String provider;

  // 소셜 로그인 제공자가 부여한 사용자 식별자 - 비밀번호 로그인 계정은 null
  @Column(name = "provider_id", length = 100)
  private String providerId;

  // 학번 (학생 전용, username과 동일 값 가능)
  @Column(name = "student_id", length = 20)
  private String studentId;

  // 소속 그룹 (예: "A반", "1학년") - 어드민 웹에서 그룹별 필터링/세션 대상 지정에 사용
  @Column(name = "group_name", length = 100)
  private String groupName;

  // 비고 - 관리자가 사용자에 대해 남기는 메모
  @Column(length = 500)
  private String note;

  // FCM 푸시 알림 토큰
  @Column(name = "fcm_token", length = 255)
  private String fcmToken;

  // 최초 비밀번호 변경 여부 - 관리자가 생성한 초기 비밀번호를 그대로 쓰고 있는지 추적
  @Builder.Default
  @Column(name = "password_changed", nullable = false)
  private Boolean passwordChanged = false;

  // 활성/비활성 상태 - 비활성화된 사용자는 로그인/출석 체크 불가 처리에 사용
  @Builder.Default
  @Column(nullable = false)
  private Boolean active = true;

  // 첫 출석 시각 - 사용자 대시보드 통계(신규 대상자 등)에 사용, 출석 전이면 null
  @Column(name = "first_attendance_at")
  private LocalDateTime firstAttendanceAt;

  // 계정 활성화 여부 (Spring Security용 - 로그인 가능 여부)
  @Builder.Default
  @Column(nullable = false)
  private Boolean enabled = true;

  // 생성 시간
  @Column(name = "created_at", nullable = false, updatable = false)
  private LocalDateTime createdAt;

  // 수정 시간
  @Column(name = "updated_at")
  private LocalDateTime updatedAt;

  // 엔티티가 처음 저장될 때 자동으로 시간 설정
  @PrePersist
  protected void onCreate() {
    createdAt = LocalDateTime.now();
    updatedAt = LocalDateTime.now();
  }

  // 엔티티가 업데이트될 때 자동으로 시간 설정
  @PreUpdate
  protected void onUpdate() {
    updatedAt = LocalDateTime.now();
  }

  // === UserDetails 인터페이스 구현 (Spring Security용) ===

  /** 사용자의 권한 목록 반환 ROLE_ 접두사를 붙여서 Spring Security 컨벤션을 따름 */
  @Override
  public Collection<? extends GrantedAuthority> getAuthorities() {
    return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
  }

  /** 계정이 만료되지 않았는지 (true = 만료 안됨) */
  @Override
  public boolean isAccountNonExpired() {
    return true;
  }

  /** 계정이 잠기지 않았는지 (true = 잠기지 않음) */
  @Override
  public boolean isAccountNonLocked() {
    return true;
  }

  /** 비밀번호가 만료되지 않았는지 (true = 만료 안됨) */
  @Override
  public boolean isCredentialsNonExpired() {
    return true;
  }

  /** 계정이 활성화되어 있는지 */
  @Override
  public boolean isEnabled() {
    return enabled;
  }

  // === 도메인 메서드 ===

  /** 첫 출석 처리 - 이미 첫 출석 시각이 기록되어 있다면 변경하지 않음 */
  public void recordFirstAttendanceIfAbsent(LocalDateTime checkInTime) {
    if (this.firstAttendanceAt == null) {
      this.firstAttendanceAt = checkInTime;
    }
  }

  /** 비밀번호 변경 완료 처리 */
  public void markPasswordChanged() {
    this.passwordChanged = true;
  }

  /** 사용자 활성화 */
  public void activate() {
    this.active = true;
  }

  /** 사용자 비활성화 */
  public void deactivate() {
    this.active = false;
  }

  /** 사용자 정보 수정 - null인 필드는 변경하지 않음 (부분 수정 지원) */
  public void updateInfo(String name, String email, String groupName, String note) {
    if (name != null) this.name = name;
    if (email != null) this.email = email;
    if (groupName != null) this.groupName = groupName;
    if (note != null) this.note = note;
  }

  /** 비밀번호 변경 - 암호화된 비밀번호를 받아 갱신하고, 최초 비밀번호 변경 여부를 true로 표시 */
  public void changePassword(String encodedPassword) {
    this.password = encodedPassword;
    this.passwordChanged = true;
  }
}
