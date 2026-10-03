package com.parksync.backend.security;

import com.parksync.backend.model.AppUser;
import com.parksync.backend.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final UserRepository users;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository users) {
        this.jwtService = jwtService;
        this.users = users;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            jwtService.parse(authorization.substring(7)).ifPresent(claims ->
                    users.findById(claims.userId()).filter(AppUser::isActive)
                            .filter(user -> user.getTokenVersion() == claims.tokenVersion())
                            .filter(user -> user.getRole().name().equals(claims.role()))
                            .ifPresent(user -> {
                                AppPrincipal principal = new AppPrincipal(user);
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        principal, null, principal.getAuthorities());
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            }));
        }
        filterChain.doFilter(request, response);
    }
}