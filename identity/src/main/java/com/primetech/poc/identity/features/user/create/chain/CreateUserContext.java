package com.primetech.poc.identity.features.user.create.chain;

import com.primetech.poc.identity.features.tenant.infrastructure.TenantEntity;
import com.primetech.poc.identity.features.user.create.CreateUserCommand;
import com.primetech.poc.identity.features.user.domain.User;
import lombok.Getter;
import lombok.Setter;

import java.util.Collections;
import java.util.List;

@Getter
@Setter
public class CreateUserContext {

  private final CreateUserCommand command;
  private User user;
  private TenantEntity tenant;
  private List<String> scopedDatabases = Collections.emptyList();

  public CreateUserContext(CreateUserCommand command) {
    this.command = command;
  }
}
