package com.attendance.global.exception;

import lombok.Getter;

/** 비즈니스 로직 예외의 최상위 클래스 - 모든 커스텀 예외의 부모 클래스. - RuntimeException을 상속받아 Unckecked Exceptiond으로 동작 */
@Getter
public class BusinessException extends RuntimeException {

  /** 에러 정보를 담고 있는 ErrorCode */
  private final ErrorCode errorCode;

  /** ErrorCode만으로 예외 생성 ErrorCode에 정의된 기본 메시지 사용 */
  public BusinessException(ErrorCode errorCode) {
    super(errorCode.getMessage()); // RuntimeException에 메시지 전달
    this.errorCode = errorCode;
  }

  /** ErrorCode와 커스텀 메시지로 예외 생성 동적으로 생성된 메시지를 사용할 때 유용 */
  public BusinessException(ErrorCode errorCode, String message) {
    super(message); // 커스텀 메시지 사용
    this.errorCode = errorCode;
  }
}
