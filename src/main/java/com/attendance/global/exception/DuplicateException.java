package com.attendance.global.exception;

/**
 * 중복된 데이터가 존재할 때 발생하는 예외
 */
public class DuplicateException extends BusinessException{

    public DuplicateException(ErrorCode errorCode) {
        super(errorCode);
    }
}
