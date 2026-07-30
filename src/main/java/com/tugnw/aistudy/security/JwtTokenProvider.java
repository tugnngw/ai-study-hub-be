package com.tugnw.aistudy.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;

import java.security.Key;
import java.util.Base64;
import java.util.Date;
import java.util.stream.Collectors;

import static org.apache.commons.compress.harmony.pack200.PackingUtils.log;

@Component
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String jwtSecret;

    @Value("${app.jwt.expiration-ms}")
    private int jwtExpirationInMs;

    @Value("${app.jwt.refresh-expiration-ms:604800000}")
    private int jwtRefreshExpirationInMs;

    private Key key;

    @PostConstruct
    public void init() {
        this.key = Keys.hmacShaKeyFor(Base64.getDecoder().decode(jwtSecret)); // Ensure proper key initialization
    }

    public String generateToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtExpirationInMs);

        String token = Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("role", userDetails.getAuthorities().stream().map(grantedAuthority -> grantedAuthority.getAuthority()).collect(Collectors.joining(",")))
                .setIssuedAt(new Date())
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
        log("DEBUG JwtTokenProvider: Generated token length: " + token.length());
        return token;
    }

    public String generateRefreshToken(Authentication authentication) {
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtRefreshExpirationInMs);

        return Jwts.builder()
                .setSubject(userDetails.getUsername())
                .claim("tokenType", "refresh")
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public String getUsernameFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith((SecretKey) key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    public int getJwtExpirationInMs() {
        return jwtExpirationInMs;
    }

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith((SecretKey) key)
                    .build()
                    .parseSignedClaims(token);

            return true;
        } catch (SignatureException e) {
            log("DEBUG JWT: Invalid signature - " + e.getMessage());
            return false;
        } catch (ExpiredJwtException e) {
            log("DEBUG JWT: Token expired - " + e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log("DEBUG JWT: Malformed token - " + e.getMessage());
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log("DEBUG JWT: Validation failed - " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return false;
        }
    }
}
