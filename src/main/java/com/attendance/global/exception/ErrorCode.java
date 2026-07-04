package com.attendance.global.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

/** 에러 코드 정의 */
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

  // 공통
  INTERNAL_SERVER_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C001", "내부 서버 오류가 발생했습니다"),
  INVALID_INPUT_VALUE(HttpStatus.BAD_REQUEST, "C002", "입력값이 올바르지 않습니다"),
  METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C003", "허용되지 않은 HTTP 메서드입니다"),
  ACCESS_DENIED(HttpStatus.FORBIDDEN, "C004", "접근 권한이 없습니다"),
  RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "C005", "요청한 리소스를 찾을 수 없습니다"),

  // 인증/인가
  UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A001", "인증이 필요합니다"),
  INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "A002", "유효하지 않은 토큰입니다"),
  EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "A003", "만료된 토큰입니다"),
  REFRESH_TOKEN_NOT_FOUND(HttpStatus.UNAUTHORIZED, "A004", "Refresh Token을 찾을 수 없습니다"),
  INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "A005", "아이디 또는 비밀번호가 일치하지 않습니다"),

  // 사용자
  USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다"),
  DUPLICATE_USERNAME(HttpStatus.CONFLICT, "U002", "이미 존재하는 아이디입니다"),
  DUPLICATE_EMAIL(HttpStatus.CONFLICT, "U003", "이미 존재하는 이메일입니다"),
  INACTIVE_USER(HttpStatus.BAD_REQUEST, "U004", "비활성화된 사용자입니다"),
  PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "U005", "현재 비밀번호가 일치하지 않습니다"),

  // NFC 태그
  NFC_TAG_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "NFC 태그를 찾을 수 없습니다"),
  DUPLICATE_NFC_UID(HttpStatus.CONFLICT, "N002", "이미 등록된 NFC UID입니다"),
  INACTIVE_NFC_TAG(HttpStatus.BAD_REQUEST, "N003", "비활성화된 NFC 태그입니다"),

  // 출석 세션
  SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "S001", "출석 세션을 찾을 수 없습니다"),
  SESSION_NOT_ACTIVE(HttpStatus.BAD_REQUEST, "S002", "활성화된 세션이 아닙니다"),
  SESSION_ALREADY_CLOSED(HttpStatus.BAD_REQUEST, "S003", "이미 종료된 세션입니다"),
  NFC_TAG_ALREADY_IN_USE(HttpStatus.CONFLICT, "S004", "해당 NFC 태그가 이미 다른 활성 세션에서 사용 중입니다"),

  // 출석 기록
  ATTENDANCE_NOT_FOUND(HttpStatus.NOT_FOUND, "AT001", "출석 기록을 찾을 수 없습니다"),
  DUPLICATE_ATTENDANCE(HttpStatus.CONFLICT, "AT002", "이미 출석 처리되었습니다"),
  ATTENDANCE_TIME_OVER(HttpStatus.BAD_REQUEST, "AT003", "출석 가능 시간이 아닙니다");

  private final HttpStatus status;
  private final String code;
  private final String message;
}