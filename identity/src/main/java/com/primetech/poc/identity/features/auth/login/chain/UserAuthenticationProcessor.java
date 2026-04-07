package com.primetech.poc.identity.features.auth.login.chain;

import com.primetech.poc.identity.features.user.domain.User;
import com.primetech.poc.identity.features.user.domain.UserStatus;
import com.primetech.poc.identity.features.user.infrastructure.UserEntity;
import com.primetech.poc.identity.features.user.infrastructure.UserJpaMapper;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Authenticates the user by username and password.
 *
 * <p>This processor runs AFTER tenant context is set, so it queries the
 * tenant's schema. It is transactional because it accesses tenant data.
 *
 * <p>Steps:
 * <ol>
 *   <li>Find user by username in tenant schema</li>
 *   <li>Check user is active</li>
 *   <li>Verify password matches</li>
 *   <li>Store authenticated user in context</li>
 * </ol>
 */
@Component
public class UserAuthenticationProcessor implements LoginProcessor {

    private static final Logger log = LoggerFactory.getLogger(UserAuthenticationProcessor.class);

    private final UserRepository userRepository;
    private final UserJpaMapper userJpaMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAuthenticationProcessor(
            UserRepository userRepository,
            UserJpaMapper userJpaMapper,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.userJpaMapper = userJpaMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    @Transactional(readOnly = true)
    public void process(LoginContext context, LoginChain chain) {
        String username = context.getCommand().username();
        String password = context.getCommand().password();
        log.debug("Authenticating user: {}", username);

        // Step 1: Find user by username (in tenant schema)
        UserEntity userEntity = userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthenticationException("Invalid username or password"));

        User user = userJpaMapper.toDomain(userEntity);

        // Step 2: Check if user is active
        if (user.status() != UserStatus.ACTIVE) {
            throw new AuthenticationException("User account is not active");
        }

        // Step 3: Verify password
        if (!passwordEncoder.matches(password, user.passwordHash())) {
            throw new AuthenticationException("Invalid username or password");
        }

        // Step 4: Store authenticated user in context
        context.setUser(user);

        log.debug("User authenticated: userId={}, username={}", user.id(), user.username().value());

        // Continue to next processor
        chain.process(context);
    }
}
