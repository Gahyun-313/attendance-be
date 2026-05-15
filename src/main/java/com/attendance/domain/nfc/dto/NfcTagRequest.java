package com.attendance.domain.nfc.dto;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * NFC 태그 생성/수정 요청 DTO
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class NfcTagRequest {

    @NotBlank(message = "UID는 필수입니다")
    @Size(max = 50, message = "UID는 50자를 초과할 수 없습니다")
    private String uid;

    @NotBlank(message = "이름은 필수입니다")
    @Size(max = 100, message = "이름은 100자를 초과할 수 없습니다")
    private String name;

    @Size(max = 255, message = "설명은 255자를 초과할 수 없습니다")
    private String description;

    @Size(max = 100, message = "위치는 100자를 초과할 수 없습니다")
    private String location;

    /**
     * Request DTO를 엔티티로 변환
     */
    public NfcTag toEntity() {
        return NfcTag.builder()
                .uid(uid)
                .name(name)
                .description(description)
                .location(location)
                .status(NfcTagStatus.ACTIVE)
                .build();
    }
}
