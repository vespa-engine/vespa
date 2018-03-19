// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.athenz.tls;

import com.yahoo.athenz.auth.util.Crypto;

import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;

/**
 * @author bjorncs
 */
public class KeyUtils {
    private KeyUtils() {}

    public static KeyPair generateKeypair(KeyAlgorithm algorithm, int keySize) {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance(algorithm.getAlgorithmName());
            if (keySize != -1) {
                keyGen.initialize(keySize);
            }
            return keyGen.genKeyPair();
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    public static KeyPair generateKeypair(KeyAlgorithm algorithm) {
        return generateKeypair(algorithm, -1);
    }

    public static PublicKey extractPublicKey(PrivateKey privateKey) {
        return Crypto.extractPublicKey(privateKey);
    }

    public static PrivateKey fromPemEncodedPrivateKey(String pem) {
        return Crypto.loadPrivateKey(pem);
    }
}
