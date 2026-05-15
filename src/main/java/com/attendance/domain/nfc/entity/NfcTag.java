package com.attendance.domain.nfc.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

/**
 * NFC 태그 엔티티
 * 출석 체크에 사용되는 NFC 태그의 정보를 저장
 */
@Entity
@Table(name = "nfc_tags", indexes = {
        @Index(name = "idx_nfc_uid", columnList = "uid"),       // UID로 빠른 조회
        @Index(name = "idx_nfc_status", columnList = "status")  // 상태별 조회
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NfcTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // NFC 태그의 고유 식별자 (UID) - AOS에서 읽은 NFC UID 값
    @Column(nullable = false, unique = true, length = 50)
    private String uid;

    // NFC 태그 이름 (관리용)
    @Column(nullable = false, length = 100)
    private String name;

    // NFC 태그 설명
    @Column(length = 255)
    private String description;

    // NFC 태그가 설치된 위치
    @Column(length = 100)
    private String location;

    // NFC 태그 상태
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NfcTagStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Builder
    public NfcTag(String uid, String name, String description, String location, NfcTagStatus status) {
        this.uid = uid;
        this.name = name;
        this.description = description;
        this.location = location;
        this.status = status != null? status : NfcTagStatus.ACTIVE;
    }

    // NFC 태그 정보 수정
    public void setUpdateInfo(String name, String description, String location) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (location != null) this.location = location;
    }

    // NFC 태그 활성화
    public void activate() { this.status = NfcTagStatus.ACTIVE; }

    // NFC 태그 비활성화
    public void deactivate() { this.status = NfcTagStatus.INACTIVE; }

    // NFC 태그가 활성 상태인지 확인
    public boolean isActive() { return this.status == NfcTagStatus.ACTIVE; }

}
