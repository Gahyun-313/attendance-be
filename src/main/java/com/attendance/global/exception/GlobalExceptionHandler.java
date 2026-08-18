package com.attendance.global.exception;

import com.attendance.global.response.ErrorResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 전역 예외 처리. @RestControllerAdvice로 모든 컨트롤러 예외를 잡아 ErrorResponse로 변환. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * BusinessException과 그 하위(InvalidValueException/EntityNotFoundException/DuplicateException) 처리
   */
  @ExceptionHandler(BusinessException.class)
  protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    log.error("BusinessException: {}", e.getMessage());
    ErrorCode errorCode = e.getErrorCode();
    ErrorResponse response = ErrorResponse.of(errorCode);
    // ErrorCode에 정의된 HTTP 상태 코드로 응답
    return new ResponseEntity<>(response, errorCode.getStatus());
  }

  /** 인가 실패 처리. @PreAuthorize 검증 실패 시 발생하며, catch-all보다 먼저 매칭돼야 403으로 응답된다. */
  @ExceptionHandler(AccessDeniedException.class)
  protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
    log.error("AccessDeniedException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.ACCESS_DENIED);
    return new ResponseEntity<>(response, ErrorCode.ACCESS_DENIED.getStatus());
  }

  /**
   * @Valid 검증 실패 처리(이메일 형식 오류, 필수값 누락, 길이 제한 초과 등)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException e) {
    log.error("MethodArgumentNotValidException: {}", e.getMessage());
    // BindingResult에서 필드 에러 정보를 추출해 ErrorResponse 생성
    ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  /** HTTP Method 오류 처리(GET만 허용하는 엔드포인트에 POST로 요청한 경우 등) */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    log.error("HttpRequestMethodNotSupportedException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
    return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
  }

  /**
   * 매핑되지 않은 경로 요청 처리. Boot 3.2+부터 NoHandlerFoundException 대신 이 예외가 던져진다. 이 핸들러가 없으면 아래
   * catch-all(Exception)이 먼저 잡아 500으로 응답해버린다.
   */
  @ExceptionHandler(NoResourceFoundException.class)
  protected ResponseEntity<ErrorResponse> handleNoResourceFoundException(
      NoResourceFoundException e) {
    log.error("NoResourceFoundException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND);
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  /** 그 외 모든 예외 처리(NullPointerException, IllegalArgumentException 등 위에서 잡히지 않은 것들) */
  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Exception: {}", e.getMessage(), e); // 스택 트레이스까지 포함해 로깅
    ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
