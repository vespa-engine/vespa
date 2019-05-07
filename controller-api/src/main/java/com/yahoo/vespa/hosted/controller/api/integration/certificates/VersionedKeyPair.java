// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.certificates;

import java.security.KeyPair;

/**
 * Represents a key pair and an unique persistence identifier
 *
 * @author mortent
 * @author andreer
 */
public class VersionedKeyPair {
    private final KeyId keyId;
    private final KeyPair keyPair;

    public VersionedKeyPair(KeyId keyId, KeyPair keyPair) {
        this.keyId = keyId;
        this.keyPair = keyPair;
    }

    public KeyId keyId() {
        return keyId;
    }

    public KeyPair keyPair() {
        return keyPair;
    }
}
