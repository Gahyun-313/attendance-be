package com.attendance.domain.attendance.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 출석 체크인 요청 DTO - 학생 앱이 NFC 스캔 후 태그 UID만 전송 (sessionId는 서버가 역추적) */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CheckInRequest {

    @NotBlank(message = "NFC 태그 UID는 필수입니다")
    private String nfcTagUid;
}
