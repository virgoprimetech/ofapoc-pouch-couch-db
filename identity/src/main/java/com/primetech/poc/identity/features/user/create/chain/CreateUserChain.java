package com.primetech.poc.identity.features.user.create.chain;

@FunctionalInterface
public interface CreateUserChain {

  void process(CreateUserContext context);
}
