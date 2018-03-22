// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

/**
 * @author bjorncs
 */
public enum KeyAlgorithm {
    RSA("RSA");

    private final String algorithmName;

    KeyAlgorithm(String algorithmName) {
        this.algorithmName = algorithmName;
    }

    String getAlgorithmName() {
        return algorithmName;
    }
}
