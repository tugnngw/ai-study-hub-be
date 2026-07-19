package com.tugnw.aistudy.security;

import com.tugnw.aistudy.domain.entity.Account;
import com.tugnw.aistudy.domain.enums.AuthProvider;
import com.tugnw.aistudy.exception.InvalidCredentialsException;
import com.tugnw.aistudy.repository.AccountRepository;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AccountRepository accountRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    @Override
    @Transactional
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        OAuth2User principal = (OAuth2User) authentication.getPrincipal();
        Account account = findOrCreateGoogleAccount(principal);
        account.setLastLoginAt(Instant.now());
        accountRepository.save(account);

        // Generate JWT token
        Authentication auth = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(account),
                null,
                new CustomUserDetails(account).getAuthorities()
        );
        String accessToken = jwtTokenProvider.generateToken(auth);

        // Redirect to frontend with JWT token
        String redirectUrl = UriComponentsBuilder.fromUriString(frontendUrl)
                .path("/oauth-success")
                .queryParam("access_token", accessToken)
                .queryParam("user_id", account.getId())
                .build()
                .toUriString();

        response.sendRedirect(redirectUrl);
    }

    private Account findOrCreateGoogleAccount(OAuth2User principal) {
        String providerId = requireAttribute(principal, "sub");
        if (!Boolean.TRUE.equals(principal.getAttribute("email_verified"))) {
            throw new IllegalArgumentException("Google email must be verified");
        }
        String email = normalizeEmail(principal.getAttribute("email"));
        String fullName = normalizeFullName(principal.getAttribute("name"), email);
        String avatar = principal.getAttribute("picture");

        return accountRepository.findByAuthProviderAndProviderIdAndDeletedAtIsNull(AuthProvider.GOOGLE, providerId)
                .map(user -> updateGoogleAccount(user, email, fullName, avatar, providerId))
                .orElseGet(() -> linkOrCreateGoogleUser(email, fullName, avatar, providerId));
    }

    private Account linkOrCreateGoogleUser(String email, String fullName, String avatarUrl, String providerId) {
        return accountRepository.findByEmailIgnoreCaseAndDeletedAtIsNull(email)
                .map(account -> {
                    if (account.getAuthProvider() != AuthProvider.GOOGLE) {
                        throw new InvalidCredentialsException("Email already registered with another login method");
                    }
                    return updateGoogleAccount(account, email, fullName, avatarUrl, providerId);
                })
                .orElseGet(() -> accountRepository.save(Account.builder()
                        .username(generateUniqueUsername(email))
                        .email(email)
                        .fullName(fullName)
                        .avatarUrl(avatarUrl)
                        .authProvider(AuthProvider.GOOGLE)
                        .providerId(providerId)
                        .build()));
    }

    private Account updateGoogleAccount(Account account, String email, String fullName, String avatar, String providerId) {
        account.setAuthProvider(AuthProvider.GOOGLE);
        account.setProviderId(providerId);
        account.setEmail(email);
        account.setFullName(account.getFullName() == null || account.getFullName().isBlank() ? fullName : account.getFullName());
        account.setAvatarUrl(account.getAvatarUrl() == null || account.getAvatarUrl().isBlank() ? avatar : account.getAvatarUrl());
        return accountRepository.save(account);
    }

    private String generateUniqueUsername(String email) {
        String base = email.substring(0, email.indexOf('@'))
                .replaceAll("[^a-zA-Z0-9_]", "_")
                .toLowerCase();
        if (base.isBlank()) {
            base = "google_user";
        }

        String username = base;
        while (accountRepository.existsByUsernameIgnoreCaseAndDeletedAtIsNull(username)) {
            username = base + "_" + UUID.randomUUID().toString().substring(0, 8);
        }
        return username;
    }

    private String normalizeEmail(Object email) {
        if (email == null || email.toString().isBlank()) {
            throw new IllegalArgumentException("Google email is required");
        }
        return email.toString().trim().toLowerCase();
    }

    private String normalizeFullName(Object name, String email) {
        return name == null || name.toString().isBlank() ? email : name.toString().trim();
    }

    private String requireAttribute(OAuth2User principal, String name) {
        Object value = principal.getAttribute(name);
        if (value == null || value.toString().isBlank()) {
            throw new IllegalArgumentException("Google " + name + " is required");
        }
        return value.toString();
    }
}