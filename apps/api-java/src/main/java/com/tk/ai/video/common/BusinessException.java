package com.tk.ai.video.common;

public class BusinessException extends BaseException {

    public BusinessException(String message) {
        super(40000, message);
    }

    public BusinessException(int code, String message) {
        super(code, message);
    }
}
