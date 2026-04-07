package com.primetech.poc.identity.features.auth.login.chain;

/**
 * Chain interface for continuing login processing.
 * <p>
 * Each processor calls {@code chain.process(context)} to pass control
 * to the next processor in the chain. The final processor does not
 * need to call this method.
 */
@FunctionalInterface
public interface LoginChain {

    /**
     * Continue processing to the next processor in the chain.
     *
     * @param context the login context to pass to the next processor
     */
    void process(LoginContext context);
}
