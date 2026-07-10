package com.attendance.domain.notification.dto;

import com.attendance.domain.notification.entity.Notification;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 알림 생성 요청 DTO */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationRequest {

    @NotBlank(message = "제목은 필수입니다")
    @Size(max = 200, message = "제목은 200자를 초과할 수 없습니다")
    private String title;

    @NotBlank(message = "내용은 필수입니다")
    @Size(max = 1000, message = "내용은 1000자를 초과할 수 없습니다")
    private String content;

    // 대상 그룹 (선택) - 비우면 전체 학생 대상
    @Size(max = 100, message = "그룹명은 100자를 초과할 수 없습니다")
    private String targetGroup;

    // 예약 발송 시각 (선택) - 비우거나 과거 시각이면 즉시 발송 시도
    private LocalDateTime scheduledAt;

    /** Request DTO를 엔티티로 변환 */
    public Notification toEntity(Long createdBy) {
        return Notification.builder()
                .title(title)
                .content(content)
                .targetGroup(targetGroup)
                .scheduledAt(scheduledAt)
                .createdBy(createdBy)
                .build();
    }
}