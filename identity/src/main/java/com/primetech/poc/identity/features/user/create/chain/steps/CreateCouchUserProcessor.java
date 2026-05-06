package com.primetech.poc.identity.features.user.create.chain.steps;

import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.shared.couchdb.CouchUserProvisioningRepository;
import org.springframework.stereotype.Component;

import static com.primetech.poc.identity.features.user.create.chain.CreateUserChainStep.CREATE_COUCH_USER;

@Component
public class CreateCouchUserProcessor implements CreateUserProcessor {

  private final CouchUserProvisioningRepository provisioningRepository;

  public CreateCouchUserProcessor(CouchUserProvisioningRepository provisioningRepository) {
    this.provisioningRepository = provisioningRepository;
  }

  @Override
  public void process(CreateUserContext context, CreateUserChain chain) {
    provisioningRepository.createUser(
        context.getCommand().username(),
        context.getCommand().password()
    );
    chain.process(context);
  }

  @Override
  public int getOrder() {
    return CREATE_COUCH_USER.order();
  }
}
