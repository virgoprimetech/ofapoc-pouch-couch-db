package com.primetech.poc.identity.shared.couchdb;

import com.primetech.poc.identity.shared.couchdb.exception.CouchDbProvisioningException;
import com.primetech.poc.identity.shared.couchdb.dto.DatabaseAccess;
import com.primetech.poc.identity.shared.couchdb.dto.create_user.CreateUserRequest;
import com.primetech.poc.identity.shared.couchdb.dto.get_security.DatabaseSecurityResponse;
import com.primetech.poc.identity.shared.couchdb.dto.update_security.UpdateSecurityRequest;
import feign.FeignException;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashSet;
import java.util.Set;

@Repository
public class CouchUserProvisioningRepository {

  private static final String COUCHDB_USER_TYPE = "user";

  private final CouchDbClient couchDbClient;

  public CouchUserProvisioningRepository(CouchDbClient couchDbClient) {
    this.couchDbClient = couchDbClient;
  }

  public void createUser(String username, String password) {
    try {
      couchDbClient.createUser(username, new CreateUserRequest(username, password, Set.of(), COUCHDB_USER_TYPE));
    } catch (FeignException.Conflict ex) {
      return;
    } catch (FeignException ex) {
      throw new CouchDbProvisioningException("Failed to create CouchDB user: " + username, ex);
    }
  }

  public boolean databaseExists(String databaseName) {
    try {
      couchDbClient.getDatabase(databaseName);
      return true;
    } catch (FeignException.NotFound ex) {
      return false;
    } catch (FeignException ex) {
      throw new CouchDbProvisioningException("Failed to check CouchDB database: " + databaseName, ex);
    }
  }

  public void createDatabase(String databaseName) {
    try {
      couchDbClient.createDatabase(databaseName);
    } catch (FeignException ex) {
      if (ex.status() == 412) {
        return;
      }
      throw new CouchDbProvisioningException("Failed to create CouchDB database: " + databaseName, ex);
    }
  }

  public void grantMemberAccess(String databaseName, String username) {
    try {
      DatabaseSecurityResponse current = couchDbClient.getSecurity(databaseName);
      DatabaseAccess admins = mergeAccess(current.admins(), null);
      DatabaseAccess members = mergeAccess(current.members(), username);
      couchDbClient.updateSecurity(databaseName, new UpdateSecurityRequest(admins, members));
    } catch (FeignException ex) {
      throw new CouchDbProvisioningException(
          "Failed to update CouchDB security for database: " + databaseName,
          ex
      );
    }
  }

  private DatabaseAccess mergeAccess(DatabaseAccess access, String usernameToAdd) {
    Set<String> names = new LinkedHashSet<>();
    Set<String> roles = new LinkedHashSet<>();

    if (access != null) {
      if (access.names() != null) {
        names.addAll(access.names());
      }
      if (access.roles() != null) {
        roles.addAll(access.roles());
      }
    }

    if (usernameToAdd != null && !usernameToAdd.isBlank()) {
      names.add(usernameToAdd);
    }

    return new DatabaseAccess(Set.copyOf(names), Set.copyOf(roles));
  }
}
