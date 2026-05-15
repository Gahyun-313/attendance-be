package com.attendance.domain.nfc.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NFC 태그 수정 요청 DTO
 * UID는 수정 불가능하므로 제외
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NfcTagUpdateRequest {

    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
    private String name;

    @Size(max = 255, message = "설명은 255자를 초과할 수 없습니다")
    private String description;

    @Size(max = 100, message = "위치는 100자를 초과할 수 없습니다")
    private String location;
}