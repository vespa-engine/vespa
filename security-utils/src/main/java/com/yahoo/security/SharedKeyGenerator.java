// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import com.yahoo.security.hpke.Aead;
import com.yahoo.security.hpke.Ciphersuite;
import com.yahoo.security.hpke.Hpke;
import com.yahoo.security.hpke.Kdf;
import com.yahoo.security.hpke.Kem;
import org.bouncycastle.crypto.engines.AESEngine;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;
import org.bouncycastle.crypto.modes.GCMBlockCipher;
import org.bouncycastle.crypto.params.AEADParameters;
import org.bouncycastle.crypto.params.KeyParameter;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;

import static com.yahoo.security.ArrayUtils.toUtf8Bytes;

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

    private static final int AES_GCM_KEY_BITS      = 128;
    private static final int AES_GCM_AUTH_TAG_BITS = 128;

    private static final int CHACHA20_POLY1305_KEY_BITS       = 256;
    private static final int CHACHA20_POLY1305_AUTH_TAG_BITS  = 128;
    private static final byte[] CHACHA20_POLY1305_KDF_CONTEXT = toUtf8Bytes("ChaCha20Poly1305 key expansion");

    private static final byte[] EMPTY_BYTES           = new byte[0];
    private static final SecureRandom SHARED_CSPRNG   = new SecureRandom();
    // Since the HPKE ciphersuite is not provided in the token, we must be very explicit about what it always is
    private static final Ciphersuite HPKE_CIPHERSUITE = Ciphersuite.of(Kem.dHKemX25519HkdfSha256(), Kdf.hkdfSha256(), Aead.aes128Gcm());
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
        return internalSealSecretKeyForReceiver(SealedSharedKey.CURRENT_TOKEN_VERSION, secretKey, receiverPublicKey, keyId);
    }

    public static SecretSharedKey fromSealedKey(SealedSharedKey sealedKey, PrivateKey receiverPrivateKey) {
        byte[] secretKeyBytes = HPKE.openBase(sealedKey.enc(), (XECPrivateKey) receiverPrivateKey,
                                              EMPTY_BYTES, sealedKey.keyId().asBytes(), sealedKey.ciphertext());
        return new SecretSharedKey(new SecretKeySpec(secretKeyBytes, "AES"), sealedKey);
    }

    public static SecretSharedKey reseal(SecretSharedKey secret, PublicKey receiverPublicKey, KeyId keyId) {
        // The resealed token must inherit the token version of the original token, or the receiver will
        // end up trying to decrypt with the wrong parameters and/or cipher.
        return internalSealSecretKeyForReceiver(secret.sealedSharedKey().tokenVersion(), secret.secretKey(), receiverPublicKey, keyId);
    }

    private static SecretSharedKey internalSealSecretKeyForReceiver(int tokenVersion, SecretKey secretKey, PublicKey receiverPublicKey, KeyId keyId) {
        // We protect the integrity of the key ID by passing it as AAD.
        var sealed = HPKE.sealBase((XECPublicKey) receiverPublicKey, EMPTY_BYTES, keyId.asBytes(), secretKey.getEncoded());
        var sealedSharedKey = new SealedSharedKey(tokenVersion, keyId, sealed.enc(), sealed.ciphertext());
        return new SecretSharedKey(secretKey, sealedSharedKey);
    }

    // A given key+IV pair can only be used for one single encryption session, ever.
    // Since our keys are intended to be inherently single-use, we can satisfy that
    // requirement even with a fixed IV. This avoids the need for explicitly including
    // the IV with the token, and also avoids tying the encryption to a particular
    // token recipient (which would be the case if the IV were deterministically derived
    // from the recipient key and ephemeral ECDH public key), as that would preclude
    // support for delegated key forwarding.
    // Both AES GCM and ChaCha20Poly1305 use a 96-bit user-supplied IV.
    private static final byte[] FIXED_96BIT_IV_FOR_SINGLE_USE_KEY = new byte[] {
            'h','e','r','e','B','d','r','a','g','o','n','s' // Nothing up my sleeve!
    };

    private static AeadCipher makeAesGcmCipher(SecretSharedKey secretSharedKey, boolean forEncryption) {
        var aeadParams = new AEADParameters(new KeyParameter(secretSharedKey.secretKey().getEncoded()),
                                            AES_GCM_AUTH_TAG_BITS, FIXED_96BIT_IV_FOR_SINGLE_USE_KEY);
        var cipher = GCMBlockCipher.newInstance(AESEngine.newInstance());
        cipher.init(forEncryption, aeadParams);
        return AeadCipher.of(cipher);
    }

    private static AeadCipher makeChaCha20Poly1305Cipher(SecretSharedKey secretSharedKey, boolean forEncryption) {
        // ChaCha20Poly1305 uses 256-bit keys, but our shared secret keys are 128 bit.
        // Deterministically derive a longer key from the existing key using a KDF.
        var expandedKey = HKDF.unsaltedExtractedFrom(secretSharedKey.secretKey().getEncoded())
                .expand(CHACHA20_POLY1305_KEY_BITS / 8, CHACHA20_POLY1305_KDF_CONTEXT);
        var aeadParams = new AEADParameters(new KeyParameter(expandedKey), CHACHA20_POLY1305_AUTH_TAG_BITS,
                                            FIXED_96BIT_IV_FOR_SINGLE_USE_KEY);
        var cipher = new ChaCha20Poly1305();
        cipher.init(forEncryption, aeadParams);
        return AeadCipher.of(new ChaCha20Poly1305AeadBlockCipherAdapter(cipher));
    }

    /**
     * Creates an AES-GCM cipher that can be used to encrypt arbitrary plaintext.
     *
     * The given secret key MUST NOT be used to encrypt more than one plaintext.
     */
    static AeadCipher makeAesGcmEncryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAesGcmCipher(secretSharedKey, true);
    }

    /**
     * Creates an AES-GCM cipher that can be used to decrypt ciphertext that was previously
     * encrypted with the given secret key.
     */
    static AeadCipher makeAesGcmDecryptionCipher(SecretSharedKey secretSharedKey) {
        return makeAesGcmCipher(secretSharedKey, false);
    }

    /**
     * Creates a ChaCha20-Poly1305 cipher that can be used to encrypt arbitrary plaintext.
     *
     * The given secret key MUST NOT be used to encrypt more than one plaintext.
     */
    static AeadCipher makeChaCha20Poly1305EncryptionCipher(SecretSharedKey secretSharedKey) {
        return makeChaCha20Poly1305Cipher(secretSharedKey, true);
    }

    /**
     * Creates a ChaCha20-Poly1305 cipher that can be used to decrypt ciphertext that was previously
     * encrypted with the given secret key.
     */
    static AeadCipher makeChaCha20Poly1305DecryptionCipher(SecretSharedKey secretSharedKey) {
        return makeChaCha20Poly1305Cipher(secretSharedKey, false);
    }

}
