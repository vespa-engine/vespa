// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import com.yahoo.security.HKDF;

/**
 * KDF implementation using HKDF-SHA256
 *
 * @author vekterli
 */
final class HkdfSha256 implements Kdf {

    private static final HkdfSha256 INSTANCE = new HkdfSha256();

    public static HkdfSha256 getInstance() { return INSTANCE; }

    @Override
    public byte[] extract(byte[] salt, byte[] labeledIkm) {
        return ((salt.length != 0) ? HKDF.extractedFrom(salt, labeledIkm)
                                   : HKDF.unsaltedExtractedFrom(labeledIkm))
                .pseudoRandomKey();
    }

    @Override
    public byte[] expand(byte[] prk, byte[] info, int nBytesToExpand) {
        return HKDF.ofPseudoRandomKey(prk).expand(nBytesToExpand, info);
    }

    @Override public short nH()    { return 32; } // HMAC-SHA256 output size
    @Override public short kdfId() { return 0x0001; } // HKDF-SHA256

}
