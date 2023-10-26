// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

/**
 * @author bjorncs
 */
public enum SignatureAlgorithm {
    SHA256_WITH_RSA("SHA256withRSA"),
    SHA512_WITH_RSA("SHA512withRSA"),
    SHA256_WITH_ECDSA("SHA256withECDSA"),
    SHA512_WITH_ECDSA("SHA512withECDSA");

    private final String algorithmName;

    SignatureAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}
