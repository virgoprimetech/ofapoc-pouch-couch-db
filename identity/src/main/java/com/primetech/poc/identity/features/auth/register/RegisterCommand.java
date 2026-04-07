package com.primetech.poc.identity.features.auth.register;

/**
 * Register Command
 */
public record RegisterCommand(
    String username,
    String email,
    String password,
    String firstName,
    String lastName
) {
}
