package com.primetech.poc.identity.features.auth.login.chain;

import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.shared.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

/**
 * Generates JWT access and refresh tokens.
 *
 * <p>This processor runs after role resolution and generates the final tokens.
 * It does not need to be transactional since it only reads from the context
 * and generates tokens (no database access).
 *
 * <p>Steps:
 * <ol>
 *   <li>Generate access token with user info and roles</li>
 *   <li>Generate refresh token</li>
 *   <li>Store tokens in context</li>
 * </ol>
 */
@Component
public class TokenGenerationProcessor implements LoginProcessor {

    private static final Logger log = LoggerFactory.getLogger(TokenGenerationProcessor.class);

    private final JwtTokenProvider jwtTokenProvider;

    public TokenGenerationProcessor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public int getOrder() {
        return 40;
    }

    @Override
    public void process(LoginContext context, LoginChain chain) {
        TenantEntity tenant = context.getTenant();
        User user = context.getUser();
        List<String> roleCodes = context.getRoleCodes();

        log.debug("Generating tokens for user: tenant={}, userId={}", tenant.getCode(), user.id());

        // Step 1: Generate access token with tenant_code in claims
        String accessToken = jwtTokenProvider.generateAccessToken(
                user.id(),
                user.username().value(),
                user.email().value(),
                roleCodes,
                Collections.emptyList()  // Use empty list for property-scoped roles
        );

        // Step 2: Generate refresh token
        String refreshToken = jwtTokenProvider.generateRefreshToken(user.id());

        // Step 3: Store tokens in context
        context.setAccessToken(accessToken);
        context.setRefreshToken(refreshToken);

        log.info("User logged in: tenant={}, userId={}", tenant.getCode(), user.id());

        // This is the final processor - no need to call chain.process()
        // But we call it anyway for consistency and extensibility
        chain.process(context);
    }
}
