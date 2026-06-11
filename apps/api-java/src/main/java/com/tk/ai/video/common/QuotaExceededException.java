package com.tk.ai.video.common;

public class QuotaExceededException extends BaseException {

    private final String quotaType;

    public QuotaExceededException(String quotaType) {
        super(40910, "Quota exceeded for type: " + quotaType);
        this.quotaType = quotaType;
    }
}
