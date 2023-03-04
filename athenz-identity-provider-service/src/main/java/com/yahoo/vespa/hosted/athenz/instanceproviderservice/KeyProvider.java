// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.athenz.instanceproviderservice;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author bjorncs
 */
public interface KeyProvider {
    PrivateKey getPrivateKey(int version);

    PublicKey getPublicKey(int version);

    default KeyPair getKeyPair(int version) {
        return new KeyPair(getPublicKey(version), getPrivateKey(version));
    }
}
