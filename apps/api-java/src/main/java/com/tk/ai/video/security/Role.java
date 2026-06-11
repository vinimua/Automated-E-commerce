package com.tk.ai.video.security;

public enum Role {
    USER,
    ADMIN;

    public String getAuthority() {
        return "ROLE_" + name();
    }
}
