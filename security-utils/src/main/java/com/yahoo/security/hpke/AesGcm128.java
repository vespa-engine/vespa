// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;

/**
 * AES-128 GCM implementation of AEAD
 *
 * @author vekterli
 */
final class AesGcm128 implements Aead {

    private static final String AEAD_CIPHER_SPEC = "AES/GCM/NoPadding";

    private static final AesGcm128 INSTANCE = new AesGcm128();

    public static AesGcm128 getInstance() { return INSTANCE; }

    /**
     * @param key Symmetric key bytes for encryption/decryption
     * @param nonce Nonce to use for the encryption/decrytion
     * @param aad Associated authenticated data that will <em>not</em> be encrypted
     * @param text Plaintext to seal or ciphertext to open, depending on cipherMode
     * @return resulting ciphertext or plaintext, depending on cipherMode
     */
    private byte[] aeadImpl(int cipherMode, byte[] key, byte[] nonce, byte[] aad, byte[] text) {
        try {
            var cipher  = Cipher.getInstance(AEAD_CIPHER_SPEC);
            var gcmSpec = new GCMParameterSpec(nT() * 8/* in bits */, nonce);
            var aesKey  = new SecretKeySpec(key, "AES");
            cipher.init(cipherMode, aesKey, gcmSpec);
            cipher.updateAAD(aad);
            return cipher.doFinal(text);
        } catch (NoSuchAlgorithmException | NoSuchPaddingException
                | InvalidKeyException | InvalidAlgorithmParameterException
                | IllegalBlockSizeException | BadPaddingException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public byte[] seal(byte[] key, byte[] nonce, byte[] aad, byte[] pt) {
        return aeadImpl(Cipher.ENCRYPT_MODE, key, nonce, aad, pt);
    }

    @Override
    public byte[] open(byte[] key, byte[] nonce, byte[] aad, byte[] ct) {
        return aeadImpl(Cipher.DECRYPT_MODE, key, nonce, aad, ct);
    }

    @Override public short nK() { return 16; } // 128-bit key
    @Override public short nN() { return 12; } // 96-bit IV
    @Override public short nT() { return 16; } // 128-bit auth tag
    @Override public short aeadId() { return 0x0001; } // AES-128-GCM

}
