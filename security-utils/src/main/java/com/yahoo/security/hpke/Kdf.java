// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

/**
 * Key derivation function (KDF)
 *
 * @author vekterli
 */
public interface Kdf {

    /**
     * Extract a pseudorandom key of fixed length {@link #nH()} bytes from input keying material
     * <code>ikm</code> and an optional byte string <code>salt</code>.
     *
     * @param salt non-secret salt used as input to KDF
     * @param labeledIkm secret input keying material
     * @return nH bytes of PRK data
     */
    byte[] extract(byte[] salt, byte[] labeledIkm);

    /**
     * Expand a pseudorandom key <code>prk</code> using optional string <code>info</code> into
     * <code>nBytesToExpand</code> bytes of output keying material.
     *
     * @param prk pseudo random key previously gotten from a call to extract.
     * @param info contextual info for expansion; useful for key domain separation
     * @param nBytesToExpand number of bytes to return
     *
     * @return <code>nBytesToExpand</code> bytes of output keying material.
     */
    byte[] expand(byte[] prk, byte[] info, int nBytesToExpand);

    /** Output size of the extract() function in bytes */
    short nH();

    /** Predefined KDF ID, as given in RFC 9180 section 7.2 */
    short kdfId();

    static Kdf hkdfSha256() {
        return HkdfSha256.getInstance();
    }

}
