package com.attendance.domain.nfc.dto;

import com.attendance.domain.nfc.entity.NfcTag;
import com.attendance.domain.nfc.entity.NfcTagStatus;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import net.bytebuddy.asm.Advice;

/** NFC 태그 응답 DTO */
@Getter
@Builder
@AllArgsConstructor
public class NfcTagResponse {

  private Long id;
  private String uid;
  private String name;
  private String description;
  private String location;
  private NfcTagStatus status;
  private LocalDateTime lastUsedAt;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 엔티티를 Response DTO로 변환 */
  public static NfcTagResponse from(NfcTag nfcTag) {
    return NfcTagResponse.builder()
        .id(nfcTag.getId())
        .uid(nfcTag.getUid())
        .name(nfcTag.getName())
        .description(nfcTag.getDescription())
        .location(nfcTag.getLocation())
        .status(nfcTag.getStatus())
        .lastUsedAt(nfcTag.getLastUsedAt())
        .createdAt(nfcTag.getCreatedAt())
        .updatedAt(nfcTag.getUpdatedAt())
        .build();
  }
}
