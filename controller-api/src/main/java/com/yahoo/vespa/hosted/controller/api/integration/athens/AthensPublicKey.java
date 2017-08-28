// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.athens;

import java.security.PublicKey;
import java.util.Objects;

/**
 * @author bjorncs
 */
public class AthensPublicKey {
    private final PublicKey publicKey;
    private final String keyId;

    public AthensPublicKey(PublicKey publicKey, String keyId) {
        this.publicKey = publicKey;
        this.keyId = keyId;
    }

    public PublicKey getPublicKey() {
        return publicKey;
    }

    public String getKeyId() {
        return keyId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AthensPublicKey that = (AthensPublicKey) o;
        return Objects.equals(publicKey, that.publicKey) &&
                Objects.equals(keyId, that.keyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicKey, keyId);
    }

    @Override
    public String toString() {
        return "AthensPublicKey{" +
                "publicKey=" + publicKey +
                ", keyId='" + keyId + '\'' +
                '}';
    }
}
