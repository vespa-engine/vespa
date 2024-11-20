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
    EC("EC", new ECGenParameterSpec("prime256v1")),
    XDH("XDH", new ECGenParameterSpec("X25519"));

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

    public static KeyAlgorithm from(String name) {
        for (var algorithm : values()) {
            if (name.equals(algorithm.getAlgorithmName())) {
                return algorithm;
            } else if (algorithm == XDH && name.equals("X25519")) {
                // "XDH" is the name used by the JDK for elliptic curve keys using Curve25519, while BouncyCastle uses
                // "X25519"
                return algorithm;
            }
        }
        throw new IllegalArgumentException("Unknown key algorithm '" + name + "'");
    }

}
