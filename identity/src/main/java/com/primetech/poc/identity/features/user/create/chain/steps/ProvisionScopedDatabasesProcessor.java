package com.primetech.poc.identity.features.user.create.chain.steps;

import com.primetech.poc.identity.features.user.create.chain.CreateUserChain;
import com.primetech.poc.identity.features.user.create.chain.CreateUserContext;
import com.primetech.poc.identity.features.user.create.chain.CreateUserProcessor;
import com.primetech.poc.identity.shared.couchdb.CouchUserProvisioningRepository;
import com.primetech.poc.identity.shared.couchdb.config.CouchDbProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import static com.primetech.poc.identity.features.user.create.chain.CreateUserChainStep.PROVISION_SCOPED_DATABASES;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProvisionScopedDatabasesProcessor implements CreateUserProcessor {

  private final CouchUserProvisioningRepository provisioningRepository;
  private final CouchDbProperties couchDbProperties;

  @Override
  public void process(CreateUserContext context, CreateUserChain chain) {
    List<String> databaseNames = couchDbProperties.getScopedDbs().stream()
        .map(scopedDb -> context.getTenant().getCode() + "$" + scopedDb)
        .toList();

    context.setScopedDatabases(databaseNames);

    for (String databaseName : databaseNames) {
      if (!provisioningRepository.databaseExists(databaseName)) {
        provisioningRepository.createDatabase(databaseName);
      }
      provisioningRepository.grantMemberAccess(databaseName, context.getUser().username().value());
    }

    chain.process(context);
  }

  @Override
  public int getOrder() {
    return PROVISION_SCOPED_DATABASES.order();
  }
}
