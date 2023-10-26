// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.crypto.BlockCipher;
import org.bouncycastle.crypto.CipherParameters;
import org.bouncycastle.crypto.DataLengthException;
import org.bouncycastle.crypto.InvalidCipherTextException;
import org.bouncycastle.crypto.modes.AEADBlockCipher;
import org.bouncycastle.crypto.modes.ChaCha20Poly1305;

/**
 * Minimal adapter to make ChaCha20Poly1305 usable as an AEADBlockCipher (it's technically
 * an AEAD stream cipher, but this is not exposed in the BouncyCastle type system).
 *
 * @author vekterli
 */
class ChaCha20Poly1305AeadBlockCipherAdapter implements AEADBlockCipher {

    private final ChaCha20Poly1305 cipher;

    ChaCha20Poly1305AeadBlockCipherAdapter(ChaCha20Poly1305 cipher) {
        this.cipher = cipher;
    }

    @Override
    public BlockCipher getUnderlyingCipher() {
        return null;
    }

    @Override
    public void init(boolean forEncryption, CipherParameters params) throws IllegalArgumentException {
        cipher.init(forEncryption, params);
    }

    @Override
    public String getAlgorithmName() {
        return cipher.getAlgorithmName();
    }

    @Override
    public void processAADByte(byte in) {
        cipher.processAADByte(in);
    }

    @Override
    public void processAADBytes(byte[] in, int inOff, int len) {
        cipher.processAADBytes(in, inOff, len);
    }

    @Override
    public int processByte(byte in, byte[] out, int outOff) throws DataLengthException {
        return cipher.processByte(in, out, outOff);
    }

    @Override
    public int processBytes(byte[] in, int inOff, int len, byte[] out, int outOff) throws DataLengthException {
        return cipher.processBytes(in, inOff, len, out, outOff);
    }

    @Override
    public int doFinal(byte[] out, int outOff) throws IllegalStateException, InvalidCipherTextException {
        return cipher.doFinal(out, outOff);
    }

    @Override
    public byte[] getMac() {
        return cipher.getMac();
    }

    @Override
    public int getUpdateOutputSize(int len) {
        return cipher.getUpdateOutputSize(len);
    }

    @Override
    public int getOutputSize(int len) {
        return cipher.getOutputSize(len);
    }

    @Override
    public void reset() {
        cipher.reset();
    }
}
