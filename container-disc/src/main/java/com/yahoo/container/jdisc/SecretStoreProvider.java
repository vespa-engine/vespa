// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.di.componentgraph.Provider;

/**
 * An secret store provider which provides a factory which throws exception on
 * invocation - as no secret store is currently provided by default.
 * The purpose of this is to provide a secret store for injection in the case where
 * no secret store component is provided.
 *
 * @author bratseth
 */
@SuppressWarnings("deprecation")
public class SecretStoreProvider implements Provider<com.yahoo.jdisc.http.SecretStore> {

    private static final ThrowingSecretStore instance = new ThrowingSecretStore();

    @Override
    public com.yahoo.jdisc.http.SecretStore get() { return instance; }

    @Override
    public void deconstruct() { }

    private static final class ThrowingSecretStore implements com.yahoo.jdisc.http.SecretStore {

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

    }

}
