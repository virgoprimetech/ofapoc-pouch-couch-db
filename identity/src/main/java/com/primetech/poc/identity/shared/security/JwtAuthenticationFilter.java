package com.primetech.poc.identity.shared.security;

import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * JWT Authentication Filter
 * <p>
 * Filters requests and authenticates JWT tokens.
 * Sets tenant context from JWT claims.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenProvider jwtTokenProvider;


  @Override
  protected void doFilterInternal(
      HttpServletRequest request,
      HttpServletResponse response,
      FilterChain filterChain
  ) throws ServletException, IOException {

    String token = resolveToken(request);

    if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
      try {
        String tenantCode = jwtTokenProvider.getTenantCode(token);
        UUID userId = jwtTokenProvider.getUserId(token);
        List<String> roles = jwtTokenProvider.getRoles(token);

        // Set tenant context from JWT (using tenant code)
        TenantContext.setTenantCode(tenantCode);

        List<SimpleGrantedAuthority> authorities = roles.stream()
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .collect(Collectors.toList());

        UserPrincipal principal = new UserPrincipal(tenantCode, userId, authorities);

        UsernamePasswordAuthenticationToken authentication =
            new UsernamePasswordAuthenticationToken(principal, null, authorities);

        SecurityContextHolder.getContext().setAuthentication(authentication);

        log.debug("Set authentication for tenant: {}, user: {}", tenantCode, userId);
      } catch (Exception e) {
        log.error("Could not set user authentication: {}", e.getMessage());
        SecurityContextHolder.clearContext();
        TenantContext.clear();
      }
    }

    try {
      filterChain.doFilter(request, response);
    } finally {
      // Always clear tenant context after request
      TenantContext.clear();
    }
  }

  private String resolveToken(HttpServletRequest request) {
    String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
    if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
      return bearerToken.substring(BEARER_PREFIX.length());
    }
    return null;
  }
}
