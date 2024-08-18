// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.container.di.componentgraph.Provider;


public class SecretsProvider implements Provider<Secrets> {

    private static final UnavailableSecrets instance = new UnavailableSecrets();

    @Override
    public Secrets get() { return instance; }

    @Override
    public void deconstruct() { }

    private static final class UnavailableSecrets implements Secrets {

        @Override
        public Secret get(String key) {
            throw new UnsupportedOperationException("Secrets is not available");
        }

    }

}
