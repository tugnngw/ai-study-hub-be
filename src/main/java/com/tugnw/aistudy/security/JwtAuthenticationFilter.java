package com.tugnw.aistudy.security;

import com.tugnw.aistudy.service.impl.CustomUserDetailsServiceImpl;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;
import org.flywaydb.core.internal.util.StringUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@AllArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final CustomUserDetailsServiceImpl userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            System.out.println("DEBUG: Token received: " + (jwt != null ? "YES (length: " + jwt.length() + ")" : "NO"));
            System.out.println("DEBUG: Request path: " + request.getRequestURI());
            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromJWT(jwt);
                System.out.println("DEBUG: Username extracted: " + username);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!userDetails.isEnabled() || !userDetails.isAccountNonLocked()) {
                    System.out.println("DEBUG: Account is locked or disabled for user: " + username);
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"ACCOUNT_LOCKED\",\"message\":\"Tài khoản của bạn đã bị khóa. Vui lòng liên hệ quản trị viên.\"}");
                    return;
                }

                // Add debug logging
                System.out.println("User Authorities: " + userDetails.getAuthorities());
                System.out.println("DEBUG: User exists and loaded");

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails,
                                null,
                                userDetails.getAuthorities()
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );
                SecurityContextHolder.getContext().setAuthentication(authentication);
                System.out.println("DEBUG: Authentication successful for user: " + username);
            } else if (StringUtils.hasText(jwt)) {
                System.out.println("DEBUG: Token validation failed");
            }
        } catch (Exception ex) {
            System.out.println("DEBUG: Exception in JWT filter: " + ex.getMessage());
            ex.printStackTrace();
            logger.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
