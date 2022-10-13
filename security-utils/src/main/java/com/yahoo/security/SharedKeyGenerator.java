// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.jcajce.provider.util.BadBlockException;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;

/**
 * Implements both the sender and receiver sides of a secure, anonymous one-way
 * key generation and exchange protocol implemented using ECIES; a hybrid crypto
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

    private static final int    AES_GCM_AUTH_TAG_BITS = 128;
    private static final String AES_GCM_ALGO_SPEC     = "AES/GCM/NoPadding";
    private static final String ECIES_CIPHER_NAME     = "ECIES"; // TODO ensure SHA-256+AES. Needs BC version bump
    private static final SecureRandom SHARED_CSPRNG   = new SecureRandom();

    public static SecretSharedKey generateForReceiverPublicKey(PublicKey receiverPublicKey, int keyId) {
        try {
            var keyGen = KeyGenerator.getInstance("AES");
            keyGen.init(256, SHARED_CSPRNG);
            var secretKey = keyGen.generateKey();

            var cipher = Cipher.getInstance(ECIES_CIPHER_NAME, BouncyCastleProviderHolder.getInstance());
            cipher.init(Cipher.ENCRYPT_MODE, receiverPublicKey);
            byte[] eciesPayload = cipher.doFinal(secretKey.getEncoded());

            var sealedSharedKey = new SealedSharedKey(keyId, eciesPayload);
            return new SecretSharedKey(secretKey, sealedSharedKey);
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    public static SecretSharedKey fromSealedKey(SealedSharedKey sealedKey, PrivateKey receiverPrivateKey) {
        try {
            var cipher = Cipher.getInstance(ECIES_CIPHER_NAME, BouncyCastleProviderHolder.getInstance());
            cipher.init(Cipher.DECRYPT_MODE, receiverPrivateKey);
            byte[] secretKey = cipher.doFinal(sealedKey.eciesPayload());

            return new SecretSharedKey(new SecretKeySpec(secretKey, "AES"), sealedKey);
        } catch (BadBlockException e) {
            throw new IllegalArgumentException("Token integrity check failed; token is either corrupt or was " +
                                               "generated for a different public key");
        } catch (NoSuchAlgorithmException | InvalidKeyException | NoSuchPaddingException
                | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    // A given key+IV pair can only be used for one single encryption session, ever.
    // Since our keys are intended to be inherently single-use, we can satisfy that
    // requirement even with a fixed IV. This avoids the need for explicitly including
    // the IV with the token, and also avoids tying the encryption to a particular
    // token recipient (which would be the case if the IV were deterministically derived
    // from the recipient key and ephemeral ECDH public key), as that would preclude
    // support for delegated key forwarding.
    private static byte[] fixed96BitIvForSingleUseKey() {
        // Nothing up my sleeve!
        return new byte[] { 'h', 'e', 'r', 'e', 'B', 'd', 'r', 'a', 'g', 'o', 'n', 's' };
    }

    private static Cipher makeAes256GcmCipher(SecretSharedKey secretSharedKey, int cipherMode) {
        try {
            var cipher  = Cipher.getInstance(AES_GCM_ALGO_SPEC);
            var gcmSpec = new GCMParameterSpec(AES_GCM_AUTH_TAG_BITS, fixed96BitIvForSingleUseKey());
            cipher.init(cipherMode, secretSharedKey.secretKey(), gcmSpec);
            return cipher;
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | InvalidAlgorithmParameterException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Creates an AES-GCM-256 Cipher that can be used to encrypt arbitrary plaintext.
     *
     * The given secret key MUST NOT be used to encrypt more than one plaintext.
     */
    public static Cipher makeAes256GcmEncryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAes256GcmCipher(secretSharedKey, Cipher.ENCRYPT_MODE);
    }

    /**
     * Creates an AES-GCM-256 Cipher that can be used to decrypt ciphertext that was previously
     * encrypted with the given secret key.
     */
    public static Cipher makeAes256GcmDecryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAes256GcmCipher(secretSharedKey, Cipher.DECRYPT_MODE);
    }

}
