package com.xianfupu.shop.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthSessionService authSessionService;

    public JwtAuthenticationFilter(JwtService jwtService, AuthSessionService authSessionService) {
        this.jwtService = jwtService;
        this.authSessionService = authSessionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                JwtService.TokenClaims claims = jwtService.verify(authorization.substring(7));
                if (!authSessionService.isActive(claims.sessionId(), claims.userId(), claims.role())) {
                    throw new IllegalArgumentException("Session revoked");
                }
                var authentication = new UsernamePasswordAuthenticationToken(
                        Long.toString(claims.userId()), null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + claims.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
