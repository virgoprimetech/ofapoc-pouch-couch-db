package com.primetech.poc.identity.features.user.create;

import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.features.user.domain.User;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;


/**
 * Create User Handler
 * <p>
 * Handles the creation of new users.
 */
@Component
@Slf4j
public class CreateUserHandler {

  private final List<CreateUserProcessor> processors;

  public CreateUserHandler(List<CreateUserProcessor> processors) {
    this.processors = processors.stream()
        .sorted(Comparator.comparingInt(CreateUserProcessor::getOrder))
        .toList();
  }

  /**
   * Handle user creation
   *
   * @param command The create command
   * @return The created user
   */
  @Transactional
  public User handle(CreateUserCommand command) {
    log.debug("Creating user with username: {}", command.username());

    CreateUserContext context = new CreateUserContext(command);
    executeChain(context);
    return context.getUser();
  }

  private void executeChain(CreateUserContext context) {
    createChain(0).process(context);
  }

  private CreateUserChain createChain(int index) {
    if (index >= processors.size()) {
      return ignored -> { };
    }

    CreateUserProcessor currentProcessor = processors.get(index);
    CreateUserChain nextChain = createChain(index + 1);
    return ctx -> currentProcessor.process(ctx, nextChain);
  }
}
