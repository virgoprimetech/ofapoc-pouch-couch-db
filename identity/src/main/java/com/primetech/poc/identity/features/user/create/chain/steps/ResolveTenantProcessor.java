package com.primetech.poc.identity.features.user.create.chain.steps;

import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.tenant.infrastructure.TenantRepository;
import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.shared.exception.EntityNotFoundException;
import com.primetech.poc.identity.shared.multitenancy.TenantContext;
import org.springframework.stereotype.Component;

import static com.primetech.poc.identity.features.user.create.chain.CreateUserChainStep.RESOLVE_TENANT;

@Component
public class ResolveTenantProcessor implements CreateUserProcessor {

  private final TenantRepository tenantRepository;

  public ResolveTenantProcessor(TenantRepository tenantRepository) {
    this.tenantRepository = tenantRepository;
  }

  @Override
  public void process(CreateUserContext context, CreateUserChain chain) {
    String tenantCode = TenantContext.getTenantCode();
    if (tenantCode == null || tenantCode.isBlank()) {
      throw new EntityNotFoundException("Tenant context is not set");
    }

    TenantEntity tenant = tenantRepository.findByCode(tenantCode)
        .orElseThrow(() -> new EntityNotFoundException("Tenant", tenantCode));

    context.setTenant(tenant);
    chain.process(context);
  }

  @Override
  public int getOrder() {
    return RESOLVE_TENANT.order();
  }
}
