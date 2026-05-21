package com.primetech.poc.identity.features.user.create.chain;

import org.springframework.core.Ordered;

public interface CreateUserProcessor extends Ordered {

  void process(CreateUserContext context, CreateUserChain chain);
}
