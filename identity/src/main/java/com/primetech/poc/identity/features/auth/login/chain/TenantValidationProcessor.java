package com.primetech.poc.identity.features.auth.login.chain;

import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantRepository;
import com.primetech.poc.identity.shared.exception.AuthenticationException;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Validates that the tenant exists and is active.
 *
 * <p>This processor runs FIRST, before any transaction is started.
 * It queries the public schema (no tenant context needed) to validate
 * the tenant, then sets the tenant context for subsequent processors.
 *
 * <p>IMPORTANT: This processor is NOT transactional because:
 * <ul>
 *   <li>It queries the public schema (no tenant isolation needed)</li>
 *   <li>Tenant context must be set BEFORE the transaction starts</li>
 *   <li>Hibernate resolves the tenant identifier when opening the connection</li>
 * </ul>
 */
@Component
public class TenantValidationProcessor implements LoginProcessor {

    private static final Logger log = LoggerFactory.getLogger(TenantValidationProcessor.class);

    private final TenantRepository tenantRepository;

    public TenantValidationProcessor(TenantRepository tenantRepository) {
        this.tenantRepository = tenantRepository;
    }

    @Override
    public int getOrder() {
        return 10;
    }

    @Override
    public void process(LoginContext context, LoginChain chain) {
        String tenantCode = context.getCommand().tenant();
        log.debug("Validating tenant: {}", tenantCode);

        // Step 1: Find tenant in public schema (no tenant context needed)
        TenantEntity tenant = tenantRepository.findByCode(tenantCode)
                .orElseThrow(() -> new AuthenticationException("Invalid tenant"));

        // Step 2: Check tenant is active
        if (!tenant.isActive()) {
            throw new AuthenticationException("Tenant is not active");
        }

        // Step 3: Store tenant in context
        context.setTenant(tenant);

        // Step 4: Set tenant context BEFORE calling chain (before transaction starts)
        // This is critical - Hibernate resolves tenant identifier when opening connection
        TenantContext.setTenant(tenant.getCode(), tenant.getId(), tenant.getSchemaName());

        log.debug("Tenant validated and context set: code={}, id={}, schema={}",
                tenant.getCode(), tenant.getId(), tenant.getSchemaName());

        try {
            // Continue to next processor
            chain.process(context);
        } finally {
            // Always clear tenant context after chain completes
            TenantContext.clear();
            log.debug("Tenant context cleared");
        }
    }
}
