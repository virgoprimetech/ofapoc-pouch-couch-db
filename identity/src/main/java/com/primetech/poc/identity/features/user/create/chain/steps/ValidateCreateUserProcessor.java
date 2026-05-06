package com.primetech.poc.identity.features.user.create.chain.steps;

import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.features.user.infrastructure.UserRepository;
import com.primetech.poc.identity.shared.exception.DuplicateEntityException;
import org.springframework.stereotype.Component;

import static com.primetech.poc.identity.features.user.create.chain.CreateUserChainStep.VALIDATE_USER;

@Component
public class ValidateCreateUserProcessor implements CreateUserProcessor {

  private final UserRepository repository;

  public ValidateCreateUserProcessor(UserRepository repository) {
    this.repository = repository;
  }

  @Override
  public void process(CreateUserContext context, CreateUserChain chain) {
    if (repository.existsByUsername(context.getCommand().username())) {
      throw new DuplicateEntityException("User", "username", context.getCommand().username());
    }

    if (repository.existsByEmail(context.getCommand().email())) {
      throw new DuplicateEntityException("User", "email", context.getCommand().email());
    }

    chain.process(context);
  }

  @Override
  public int getOrder() {
    return VALIDATE_USER.order();
  }
}
