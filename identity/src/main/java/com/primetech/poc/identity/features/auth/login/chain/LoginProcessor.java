package com.primetech.poc.identity.features.auth.login.chain;

import org.springframework.core.Ordered;

/** Processor interface for the login chain of responsibility. */
public interface LoginProcessor extends Ordered {

    /**
     * Process the login request.
     *
     * @param context the mutable login context
     * @param chain the chain to continue processing
     */
    void process(LoginContext context, LoginChain chain);
}
