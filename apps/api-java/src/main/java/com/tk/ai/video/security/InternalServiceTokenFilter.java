package com.tk.ai.video.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Validates X-Internal-Service-Token for callback endpoints.
 * Applied only to /api/ai-callbacks/** and /api/render-callbacks/**
 */
@Slf4j
@Component
public class InternalServiceTokenFilter extends OncePerRequestFilter {

    private static final List<String> PROTECTED_PATHS = List.of(
            "/api/ai-callbacks/**",
            "/api/render-callbacks/**"
    );

    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    @Value("${internal-service.token}")
    private String internalToken;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getRequestURI();

        boolean isProtectedPath = PROTECTED_PATHS.stream()
                .anyMatch(pattern -> pathMatcher.match(pattern, path));

        if (isProtectedPath) {
            String token = request.getHeader("X-Internal-Service-Token");
            if (!StringUtils.hasText(token) || !internalToken.equals(token)) {
                log.warn("Invalid or missing internal service token for path: {}", path);
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Invalid internal service token");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }
}
