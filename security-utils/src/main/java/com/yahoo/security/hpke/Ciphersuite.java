// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

/**
 * A Ciphersuite is a 3-tuple that encapsulates the necessary primitives to use HKDF:
 *
 * <ul>
 *     <li>A key encapsulation mechanism (KEM)</li>
 *     <li>A key derivation function (KDF)</li>
 *     <li>An "authenticated encryption with associated data" (AEAD) algorithm</li>
 * </ul>
 *
 * @author vekterli
 */
public record Ciphersuite(Kem kem, Kdf kdf, Aead aead) {

    public static Ciphersuite of(Kem kem, Kdf kdf, Aead aead) {
        return new Ciphersuite(kem, kdf, aead);
    }

    /**
     * Returns a Ciphersuite of DHKEM(X25519, HKDF-SHA256), HKDF-SHA256, AES-128-GCM
     */
    public static Ciphersuite defaultSuite() {
        return Ciphersuite.of(Kem.dHKemX25519HkdfSha256(), Kdf.hkdfSha256(), Aead.aesGcm128());
    }

}
