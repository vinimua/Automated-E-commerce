package com.tk.ai.video.module.auth.service;

import com.tk.ai.video.module.auth.dto.*;

public interface AuthService {

    AuthResponse register(RegisterRequest request);

    AuthResponse login(LoginRequest request);

    AuthResponse refresh(RefreshTokenRequest request);
}
