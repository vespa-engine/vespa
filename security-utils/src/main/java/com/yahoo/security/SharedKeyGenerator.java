// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import com.yahoo.security.hpke.Aead;
import com.yahoo.security.hpke.Ciphersuite;
import com.yahoo.security.hpke.Hpke;
import com.yahoo.security.hpke.Kdf;
import com.yahoo.security.hpke.Kem;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

/**
 * Implements both the sender and receiver sides of a secure, anonymous one-way
 * key generation and exchange protocol implemented using HPKE; a hybrid crypto
 * scheme built around elliptic curves.
 *
 * A shared key, once generated, may have its sealed component sent over a public
 * channel without revealing anything about the underlying secret key. Only a
 * recipient holding the private key corresponding to the public used for shared
 * key creation may derive the same secret key as the sender.
 *
 * Every generated key is globally unique (with extremely high probability).
 *
 * The secret key is intended to be used <em>only once</em>. It MUST NOT be used to
 * produce more than a single ciphertext. Using the secret key to produce multiple
 * ciphertexts completely breaks the security model due to using a fixed Initialization
 * Vector (IV).
 */
public class SharedKeyGenerator {

    private static final int    AES_GCM_KEY_BITS      = 128;
    private static final int    AES_GCM_AUTH_TAG_BITS = 128;
    private static final String AES_GCM_ALGO_SPEC     = "AES/GCM/NoPadding";
    private static final byte[] EMPTY_BYTES           = new byte[0];
    private static final SecureRandom SHARED_CSPRNG   = new SecureRandom();
    // Since the HPKE ciphersuite is not provided in the token, we must be very explicit about what it always is
    private static final Ciphersuite HPKE_CIPHERSUITE = Ciphersuite.of(Kem.dHKemX25519HkdfSha256(), Kdf.hkdfSha256(), Aead.aesGcm128());
    private static final Hpke HPKE = Hpke.of(HPKE_CIPHERSUITE);

    private static SecretKey generateRandomSecretAesKey() {
        try {
            var keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(AES_GCM_KEY_BITS, SHARED_CSPRNG);
            return keyGen.generateKey();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static SecretSharedKey generateForReceiverPublicKey(PublicKey receiverPublicKey, KeyId keyId) {
        var secretKey = generateRandomSecretAesKey();
        return internalSealSecretKeyForReceiver(secretKey, receiverPublicKey, keyId);
    }

    public static SecretSharedKey fromSealedKey(SealedSharedKey sealedKey, PrivateKey receiverPrivateKey) {
        byte[] secretKeyBytes = HPKE.openBase(sealedKey.enc(), (XECPrivateKey) receiverPrivateKey,
                                              EMPTY_BYTES, sealedKey.keyId().asBytes(), sealedKey.ciphertext());
        return new SecretSharedKey(new SecretKeySpec(secretKeyBytes, "AES"), sealedKey);
    }

    public static SecretSharedKey reseal(SecretSharedKey secret, PublicKey receiverPublicKey, KeyId keyId) {
        return internalSealSecretKeyForReceiver(secret.secretKey(), receiverPublicKey, keyId);
    }

    private static SecretSharedKey internalSealSecretKeyForReceiver(SecretKey secretKey, PublicKey receiverPublicKey, KeyId keyId) {
        // We protect the integrity of the key ID by passing it as AAD.
        var sealed = HPKE.sealBase((XECPublicKey) receiverPublicKey, EMPTY_BYTES, keyId.asBytes(), secretKey.getEncoded());
        var sealedSharedKey = new SealedSharedKey(keyId, sealed.enc(), sealed.ciphertext());
        return new SecretSharedKey(secretKey, sealedSharedKey);
    }

    // A given key+IV pair can only be used for one single encryption session, ever.
    // Since our keys are intended to be inherently single-use, we can satisfy that
    // requirement even with a fixed IV. This avoids the need for explicitly including
    // the IV with the token, and also avoids tying the encryption to a particular
    // token recipient (which would be the case if the IV were deterministically derived
    // from the recipient key and ephemeral ECDH public key), as that would preclude
    // support for delegated key forwarding.
    private static final byte[] FIXED_96BIT_IV_FOR_SINGLE_USE_KEY = new byte[] {
            'h','e','r','e','B','d','r','a','g','o','n','s' // Nothing up my sleeve!
    };

    private static Cipher makeAesGcmCipher(SecretSharedKey secretSharedKey, int cipherMode) {
        try {
            var cipher  = Cipher.getInstance(AES_GCM_ALGO_SPEC);
            var gcmSpec = new GCMParameterSpec(AES_GCM_AUTH_TAG_BITS, FIXED_96BIT_IV_FOR_SINGLE_USE_KEY);
            cipher.init(cipherMode, secretSharedKey.secretKey(), gcmSpec);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an AES-GCM Cipher that can be used to encrypt arbitrary plaintext.
     *
     * The given secret key MUST NOT be used to encrypt more than one plaintext.
     */
    public static Cipher makeAesGcmEncryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAesGcmCipher(secretSharedKey, Cipher.ENCRYPT_MODE);
    }

    /**
     * Creates an AES-GCM Cipher that can be used to decrypt ciphertext that was previously
     * encrypted with the given secret key.
     */
    public static Cipher makeAesGcmDecryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAesGcmCipher(secretSharedKey, Cipher.DECRYPT_MODE);
    }

}
