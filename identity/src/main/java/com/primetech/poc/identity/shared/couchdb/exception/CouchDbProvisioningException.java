package com.primetech.poc.identity.shared.couchdb.exception;

import com.primetech.poc.identity.shared.exception.BusinessException;

public class CouchDbProvisioningException extends BusinessException {

  public CouchDbProvisioningException(String message) {
    super("COUCHDB_PROVISIONING_FAILED", message);
  }

  public CouchDbProvisioningException(String message, Throwable cause) {
    super("COUCHDB_PROVISIONING_FAILED", message, cause);
  }
}
