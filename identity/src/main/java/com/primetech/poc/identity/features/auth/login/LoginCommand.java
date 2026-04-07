package com.primetech.poc.identity.features.auth.login;

/**
 * Login Command
 */
public record LoginCommand(String tenant, String username, String password) {
}
