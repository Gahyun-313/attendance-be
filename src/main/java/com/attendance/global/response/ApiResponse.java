package com.attendance.global.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/** 공통 API 응답 형식 */
@Getter
@Builder
@AllArgsConstructor
public class ApiResponse<T> {

  private boolean success;
  private T data;
  private String message;

  /** 성공 응답 (데이터 포함) */
  public static <T> ApiResponse<T> success(T data, String message) {
    return ApiResponse.<T>builder().success(true).data(data).message(message).build();
  }

  /** 성공 응답 (데이터 없음) */
  public static <T> ApiResponse<T> success(String message) {
    return ApiResponse.<T>builder().success(true).data(null).message(message).build();
  }
}
