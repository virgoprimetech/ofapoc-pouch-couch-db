package com.primetech.poc.identity.features.auth.login;

import com.primetech.poc.identity.features.auth.login.chain.LoginChain;
import com.primetech.poc.identity.features.auth.login.chain.LoginContext;
import com.primetech.poc.identity.features.auth.login.chain.LoginProcessor;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.shared.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

/**
 * Login Handler - Chain of Responsibility Implementation
 * <p>
 * Orchestrates the login flow by delegating to a chain of processors.
 * Each processor handles a specific step in the authentication process.
 * <p>
 * Processing Order:
 * <ol>
 *   <li>{@link com.primetech.poc.identity.features.auth.login.chain.TenantValidationProcessor}
 *       - Validates tenant exists and is active (public schema, no transaction)</li>
 *   <li>{@link com.primetech.poc.identity.features.auth.login.chain.UserAuthenticationProcessor}
 *       - Authenticates user by username/password (tenant schema, transactional)</li>
 *   <li>{@link com.primetech.poc.identity.features.auth.login.chain.RoleResolutionProcessor}
 *       - Resolves user's roles and permissions (tenant schema, transactional)</li>
 *   <li>{@link com.primetech.poc.identity.features.auth.login.chain.TokenGenerationProcessor}
 *       - Generates JWT tokens (no database access)</li>
 * </ol>
 * <p>
 * IMPORTANT: This handler is NOT @Transactional because:
 * <ul>
 *   <li>Tenant validation must happen before tenant context is set</li>
 *   <li>Tenant context must be set BEFORE the transaction starts</li>
 *   <li>Individual processors handle their own transactions as needed</li>
 * </ul>
 *
 * @see LoginProcessor
 * @see LoginContext
 * @see LoginChain
 */
@Component
public class LoginHandler {

    private static final Logger log = LoggerFactory.getLogger(LoginHandler.class);

    private final List<LoginProcessor> processors;
    private final JwtTokenProvider jwtTokenProvider;

    public LoginHandler(List<LoginProcessor> processors, JwtTokenProvider jwtTokenProvider) {
        // Sort processors by order to ensure correct execution sequence
        this.processors = processors.stream()
                .sorted(Comparator.comparingInt(LoginProcessor::getOrder))
                .toList();
        this.jwtTokenProvider = jwtTokenProvider;
        log.debug("LoginHandler initialized with {} processors", processors.size());
    }

    /**
     * Main entry point for login.
     * <p>
     * NOT @Transactional because tenant context must be set before transaction starts.
     * Individual processors manage their own transactions as needed.
     *
     * @param command the login command containing tenant, username, and password
     * @return the login response with tokens and user info
     * @throws com.primetech.poc.identity.shared.exception.AuthenticationException if authentication fails
     */
    public LoginResponse handle(LoginCommand command) {
        log.debug("Login attempt for tenant: {}, username: {}", command.tenant(), command.username());

        // Create the context that will be passed through the chain
        LoginContext context = new LoginContext(command);

        // Build and execute the chain
        executeChain(context);

        // Build and return the response from the context
        return buildResponse(context);
    }

    /**
     * Execute the chain of processors.
     * <p>
     * Creates a chain of processors where each processor calls the next one
     * via the LoginChain interface.
     */
    private void executeChain(LoginContext context) {
        // Create the chain starting from index 0
        LoginChain chain = createChain(0);
        chain.process(context);
    }

    /**
     * Create a chain starting at the given processor index.
     * <p>
     * Uses a recursive approach where each chain element wraps the next.
     * The final chain is a no-op (end of chain).
     */
    private LoginChain createChain(int index) {
        if (index >= processors.size()) {
            // End of chain - no-op
            return ctx -> { };
        }

        LoginProcessor currentProcessor = processors.get(index);
        LoginChain nextChain = createChain(index + 1);

        return ctx -> currentProcessor.process(ctx, nextChain);
    }

    /**
     * Build the login response from the context.
     */
    private LoginResponse buildResponse(LoginContext context) {
        TenantEntity tenant = context.getTenant();
        User user = context.getUser();

        return new LoginResponse(
                context.getAccessToken(),
                context.getRefreshToken(),
                "Bearer",
                jwtTokenProvider.getAccessTokenExpirationMs() / 1000,
                tenant.getId(),
                user.id(),
                user.username().value(),
                user.email().value(),
                context.getRoleCodes()
        );
    }
}
