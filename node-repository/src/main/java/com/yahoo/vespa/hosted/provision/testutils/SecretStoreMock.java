// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.testutils;

import ai.vespa.secret.internal.TypedSecretStore;
import ai.vespa.secret.model.Key;
import ai.vespa.secret.model.Secret;
import ai.vespa.secret.model.SecretVersionId;
import com.yahoo.component.AbstractComponent;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * @author mpolden
 */
public class SecretStoreMock extends AbstractComponent implements TypedSecretStore {

    private final Set<Secret> secrets = new HashSet<>();

    @Override
    public Secret getSecret(Key key) {
        return findSecret(key, Optional.empty());
    }

    @Override
    public Secret getSecret(Key key, SecretVersionId version) {
        return findSecret(key, Optional.of(version));
    }

    @Override
    public Type type() {
        return Type.TEST;
    }

    @Override
    public String getSecret(String key) {
        return getSecret(Key.fromString(key)).secretAsString();
    }

    @Override
    public String getSecret(String key, int version) {
        return getSecret(Key.fromString(key), SecretVersionId.of(Integer.toString(version))).secretAsString();
    }

    public SecretStoreMock add(Secret secret) {
        secrets.add(secret);
        return this;
    }

    private Secret findSecret(Key key, Optional<SecretVersionId> version) {
        return secrets.stream().filter(s -> s.secretName().equals(key.secretName()) &&
                                            s.vaultName().equals(key.vaultName()) &&
                                            (version.isEmpty() || version.get().equals(s.version())))
                      .min(Comparator.naturalOrder()) // Choose the highest version. For some reason Secret::compareTo sorts version in reverse order?!
                      .orElseThrow(() -> new IllegalArgumentException("No secret found for key=" + key +
                                                                      ", version=" + version.map(Record::toString).orElse("any")));
    }

}
