// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.container.jdisc.secretstore;

/**
 * @author mortent
 */
public interface SecretStore {
    /** Returns the secret for this key */
    String getSecret(String key);

    /** Returns the secret for this key and version */
    String getSecret(String key, int version);
}
