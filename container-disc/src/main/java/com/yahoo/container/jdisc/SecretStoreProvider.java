// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import com.yahoo.container.jdisc.secretstore.SecretNotFoundException;
import com.yahoo.container.jdisc.secretstore.SecretStore;
import com.yahoo.container.di.componentgraph.Provider;

import java.util.List;

public class SecretStoreProvider implements Provider<SecretStore> {

    private static final ThrowingSecretStore instance = new ThrowingSecretStore();

    @Override
    public SecretStore get() { return instance; }

    @Override
    public void deconstruct() { }

    private static final class ThrowingSecretStore implements SecretStore {

        @Override
        public String getSecret(String key) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

        @Override
        public String getSecret(String key, int version) {
            throw new UnsupportedOperationException("A secret store is not available");
        }

        @Override
        public List<Integer> listSecretVersions(String key) {
            throw new SecretNotFoundException("A secret store is not available");
        }
    }

}
