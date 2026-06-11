package com.tk.ai.video.common;

public class InvalidStateTransitionException extends BaseException {

    public InvalidStateTransitionException(String current, String target) {
        super(40920, "Invalid state transition: " + current + " -> " + target);
    }
}
