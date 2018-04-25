// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.http;

/**
 * An abstraction of a secret store for e.g passwords.
 * Implementations can be plugged in to provide passwords for various keys.
 *
 * @author bratseth
 * @author bjorncs
 * @deprecated Use com.yahoo.container.jdisc.secretstore.SecretStore
 */
@Deprecated
public interface SecretStore {

    /** Returns the secret for this key */
    String getSecret(String key);

    /** Returns the secret for this key and version */
    default String getSecret(String key, int version) {
        throw new UnsupportedOperationException("SecretStore implementation does not support versioned secrets");
    }

}
