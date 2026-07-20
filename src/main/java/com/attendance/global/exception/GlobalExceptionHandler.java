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

/**
 * 전역 예외 처리 핸들러 @RestControllerAdvice: 모든 @RestController에서 발생하는 예외를 잡아서 처리 각 예외 유형별로 적절한
 * ErrorResponse를 생성하여 클라이언트에 반환
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * BusinessException 및 그 하위 예외 처리 - InvalidValueException - EntityNotFoundException -
   * DuplicateException
   */
  @ExceptionHandler(BusinessException.class)
  protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
    log.error("BusinessException: {}", e.getMessage());
    ErrorCode errorCode = e.getErrorCode();
    ErrorResponse response = ErrorResponse.of(errorCode);
    // ErrorCode에 정의된 HTTP 상태 코드로 응답
    return new ResponseEntity<>(response, errorCode.getStatus());
  }

  /**
   * 인가(권한) 실패 예외 처리 - @PreAuthorize("hasRole(...)") 검증 실패 시 Spring Security가 던지는 예외 - 아래
   * catch-all(Exception.class) 핸들러보다 먼저 선언/매칭되어야 500이 아닌 403으로 응답됨
   */
  @ExceptionHandler(AccessDeniedException.class)
  protected ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException e) {
    log.error("AccessDeniedException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.ACCESS_DENIED);
    return new ResponseEntity<>(response, ErrorCode.ACCESS_DENIED.getStatus());
  }

  /**
   * Validation 예외 처리 (@Valid 실패) - @RequestBody에 @Valid를 사용했을 떄 검증 실패 시 발생 (ex. 이메일 형식 오류, 필수값 누락,
   * 길이 제한 초과 등)
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  protected ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
      MethodArgumentNotValidException e) {
    log.error("MethodArgumentNotValidException: {}", e.getMessage());
    // BindingResult에서 필드 에러 정보를 추출하여 ErrorResponse 생성
    ErrorResponse response = ErrorResponse.of(ErrorCode.INVALID_INPUT_VALUE, e.getBindingResult());
    return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
  }

  /** HTTP Method 오류 처리 - GET 요청만 허용하는 엔드포인트에 POST로 요청한 경우 등 */
  @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
  protected ResponseEntity<ErrorResponse> handleHttpRequestMethodNotSupportedException(
      HttpRequestMethodNotSupportedException e) {
    log.error("HttpRequestMethodNotSupportedException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED);
    return new ResponseEntity<>(response, HttpStatus.METHOD_NOT_ALLOWED);
  }

  /**
   * 매핑되지 않은 경로 요청 처리 - 삭제되었거나 존재하지 않는 엔드포인트 호출 시 발생 - Spring 6.1+/Boot 3.2+부터는
   * NoHandlerFoundException 대신 이 예외(NoResourceFoundException)가 던져짐 - 이 핸들러가 없으면 아래
   * catch-all(Exception.class)이 먼저 가로채서 500으로 응답해버림
   */
  @ExceptionHandler(NoResourceFoundException.class)
  protected ResponseEntity<ErrorResponse> handleNoResourceFoundException(
      NoResourceFoundException e) {
    log.error("NoResourceFoundException: {}", e.getMessage());
    ErrorResponse response = ErrorResponse.of(ErrorCode.RESOURCE_NOT_FOUND);
    return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
  }

  /**
   * 그 외 모든 예외 처리 - 위에서 처리되지 않은 모든 예외를 여기서 잡아서 처리 - NullPointerException, IllegalArgumentException 등
   */
  @ExceptionHandler(Exception.class)
  protected ResponseEntity<ErrorResponse> handleException(Exception e) {
    log.error("Exception: {}", e.getMessage(), e); // 스택 트레이스 포함 로깅
    ErrorResponse response = ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR);
    return new ResponseEntity<>(response, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
