// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.security.GeneralSecurityException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;

/**
 * Misc signature utils
 *
 * @author bjorncs
 */
public class SignatureUtils {

    /** Returns a signature instance which computes a hash of its content, before signing with the given private key. */
    public static Signature createSigner(PrivateKey key, SignatureAlgorithm algorithm) {
        try {
            Signature signer = Signature.getInstance(algorithm.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
            signer.initSign(key);
            return signer;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Returns a signature instance which computes a hash of its content, before verifying with the given public key. */
    public static Signature createVerifier(PublicKey key, SignatureAlgorithm algorithm) {
        try {
            Signature signer = Signature.getInstance(algorithm.getAlgorithmName(), BouncyCastleProviderHolder.getInstance());
            signer.initVerify(key);
            return signer;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
