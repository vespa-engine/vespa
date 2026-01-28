// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.container.jdisc;

import ai.vespa.secret.Secret;
import ai.vespa.secret.Secrets;
import com.yahoo.container.di.componentgraph.Provider;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/**
 * The default {@link Secrets} provider instance if no other is configured.
 *
 * @author Lester Solbakken
 * @author bjorncs
 */
public class SecretsProvider implements Provider<Secrets> {

    private static final EnvironmentSecrets instance = new EnvironmentSecrets();

    @Override public Secrets get() { return instance; }
    @Override public void deconstruct() { }

    /** Implementation of {@link Secrets} that reads secret values from environment variables. */
    static class EnvironmentSecrets implements Secrets {
        private static final Logger log = Logger.getLogger(EnvironmentSecrets.class.getName());
        private final AtomicBoolean firstCall = new AtomicBoolean(true);

        @Override
        public Secret get(String key) {
            if (firstCall.compareAndSet(true, false)) {
                log.info("Using environment variable based 'Secrets' implementation");
            }
            var envName = toEnvName(key);
            var value = System.getenv(envName);
            if (value == null || value.isEmpty()) {
                throw new IllegalArgumentException(
                        "Secret '%s' not found. Set environment variable '%s'".formatted(key, envName));
            }
            return () -> value;
        }

        static String toEnvName(String key) {
            var result = new StringBuilder();
            for (int i = 0; i < key.length(); i++) {
                char c = key.charAt(i);
                if (Character.isUpperCase(c) && i > 0 && Character.isLowerCase(key.charAt(i - 1))) {
                    result.append('_');
                }
                result.append(Character.toUpperCase(c));
            }
            return "VESPA_SECRET_" + result;
        }
    }

}
