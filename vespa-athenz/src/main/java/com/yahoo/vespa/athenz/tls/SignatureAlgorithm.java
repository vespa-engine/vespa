// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

/**
 * @author bjorncs
 */
public enum SignatureAlgorithm {
    SHA256_WITH_RSA("SHA256withRSA"),
    SHA512_WITH_RSA("SHA512withRSA");

    private final String algorithmName;

    SignatureAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    public String getAlgorithmName() {
        return algorithmName;
    }
}
