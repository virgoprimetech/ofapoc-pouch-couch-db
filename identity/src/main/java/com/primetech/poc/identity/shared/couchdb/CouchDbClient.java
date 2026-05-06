package com.primetech.poc.identity.shared.couchdb;

import com.primetech.poc.identity.shared.couchdb.config.CouchDbFeignConfig;
import com.primetech.poc.identity.shared.couchdb.dto.create_db.CreateDatabaseResponse;
import com.primetech.poc.identity.shared.couchdb.dto.create_user.CreateUserRequest;
import com.primetech.poc.identity.shared.couchdb.dto.create_user.CreateUserResponse;
import com.primetech.poc.identity.shared.couchdb.dto.get_db.DatabaseInfoResponse;
import com.primetech.poc.identity.shared.couchdb.dto.get_security.DatabaseSecurityResponse;
import com.primetech.poc.identity.shared.couchdb.dto.update_security.UpdateSecurityRequest;
import com.primetech.poc.identity.shared.couchdb.dto.update_security.UpdateSecurityResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "couchdb", url = "${app.couchdb.url}", configuration = CouchDbFeignConfig.class)
public interface CouchDbClient {

  @PutMapping("/{db}")
  CreateDatabaseResponse createDatabase(@PathVariable String db);

  @GetMapping("/{db}")
  DatabaseInfoResponse getDatabase(@PathVariable String db);

  @GetMapping("/{db}/_security")
  DatabaseSecurityResponse getSecurity(@PathVariable String db);

  @PutMapping(path = "/{db}/_security", consumes = MediaType.APPLICATION_JSON_VALUE)
  UpdateSecurityResponse updateSecurity(
      @PathVariable String db,
      @RequestBody UpdateSecurityRequest request);

  @PutMapping(path = "/_users/org.couchdb.user:{username}", consumes = MediaType.APPLICATION_JSON_VALUE)
  CreateUserResponse createUser(
      @PathVariable String username,
      @RequestBody CreateUserRequest request);
}
