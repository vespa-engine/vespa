// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.security.spec.AlgorithmParameterSpec;
import java.security.spec.ECGenParameterSpec;
import java.util.Optional;

/**
 * @author bjorncs
 */
public enum KeyAlgorithm {
    RSA("RSA", null),
    EC("EC", new ECGenParameterSpec("prime256v1")); // TODO Make curve configurable

    final String algorithmName;
    private final AlgorithmParameterSpec spec;

    KeyAlgorithm(String algorithmName, AlgorithmParameterSpec spec) {
        this.algorithmName = algorithmName;
        this.spec = spec;
    }

    String getAlgorithmName() {
        return algorithmName;
    }

    Optional<AlgorithmParameterSpec> getSpec() { return Optional.ofNullable(spec); }
}
