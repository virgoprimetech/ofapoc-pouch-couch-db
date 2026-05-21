package com.primetech.poc.identity.features.user.create.chain;

import org.springframework.core.Ordered;

public enum CreateUserChainStep {
  VALIDATE_USER(Ordered.HIGHEST_PRECEDENCE),
  RESOLVE_TENANT(Ordered.HIGHEST_PRECEDENCE + 10),
  PERSIST_USER(Ordered.HIGHEST_PRECEDENCE + 20),
  CREATE_COUCH_USER(Ordered.HIGHEST_PRECEDENCE + 30),
  PROVISION_SCOPED_DATABASES(Ordered.HIGHEST_PRECEDENCE + 40);

  private final int order;

  CreateUserChainStep(int order) {
    this.order = order;
  }

  public int order() {
    return order;
  }
}
