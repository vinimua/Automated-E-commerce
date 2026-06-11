package com.tk.ai.video.common;

public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String message) {
        super(40400, message);
    }

    public ResourceNotFoundException(String resource, Object id) {
        super(40400, resource + " not found: " + id);
    }
}
