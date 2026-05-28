package com.attendance.domain.nfc.entity;

/** NFC 태그 상태를 나타내는 Enum */
public enum NfcTagStatus {
  ACTIVE, // 활성 상태
  INACTIVE, // 비활성 상태
  LOST, // 분실
  DAMAGED // 손상됨
}
