package com.tk.ai.video.security;

import com.tk.ai.video.config.JwtConfig;
import io.jsonwebtoken.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    public String createAccessToken(UUID userId, String email, Role role) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getAccessTokenExpiration());

        return Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(userId.toString())
                .claim("email", email)
                .claim("role", role.name())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtConfig.getSigningKey())
                .compact();
    }

    public String createRefreshToken(UUID userId) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getRefreshTokenExpiration());

        return Jwts.builder()
                .issuer(jwtConfig.getIssuer())
                .subject(userId.toString())
                .claim("type", "refresh")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(jwtConfig.getSigningKey())
                .compact();
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(jwtConfig.getSigningKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT: {}", e.getMessage());
            return false;
        }
    }

    public UUID getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return UUID.fromString(claims.getSubject());
    }

    public String getEmailFromToken(String token) {
        return parseClaims(token).get("email", String.class);
    }

    public Role getRoleFromToken(String token) {
        String roleStr = parseClaims(token).get("role", String.class);
        return roleStr != null ? Role.valueOf(roleStr) : Role.USER;
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(parseClaims(token).get("type", String.class));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(jwtConfig.getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
