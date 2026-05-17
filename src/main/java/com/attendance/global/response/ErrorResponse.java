package com.attendance.global.response;

import com.attendance.global.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 에러 응답 DTO
 */
@Getter
@Builder
@AllArgsConstructor
public class ErrorResponse {

    private String code;
    private String message;
    private int status;
    private LocalDateTime timestamp;
    private List<FieldError> error;

    /**
     * ErrorCode로 ErrorResponse 생성
     */
    public static ErrorResponse of(ErrorCode errorCode) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .status(errorCode.getStatus().value())
                .timestamp(LocalDateTime.now())
                .error(new ArrayList<>())
                .build();
    }

    /**
     * ErrorCode와 BindingResult로 ErrorResponse 생성 (Validation 에러용)
     */
    public static ErrorResponse of(ErrorCode errorCode, BindingResult bindingResult) {
        return ErrorResponse.builder()
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .status(errorCode.getStatus().value())
                .timestamp(LocalDateTime.now())
                .error(FieldError.of(bindingResult))
                .build();
    }

    /**
     * 필드 에러 정보
     */
    @Getter
    @AllArgsConstructor
    public static class FieldError {
        private String field;
        private String value;
        private String reason;

        private static List<FieldError> of(BindingResult bindingResult) {
            List<org.springframework.validation.FieldError> fieldErrors = bindingResult.getFieldErrors();
            List<FieldError> errors = new ArrayList<>();

            for (org.springframework.validation.FieldError error : fieldErrors) {
                errors.add(new FieldError(
                        error.getField(),
                        error.getRejectedValue() == null ? "" : error.getRejectedValue().toString(),
                        error.getDefaultMessage()
                ));
            }

            return errors;
        }
    }
}
