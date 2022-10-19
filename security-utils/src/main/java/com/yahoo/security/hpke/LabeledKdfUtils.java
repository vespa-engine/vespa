// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import static com.yahoo.security.ArrayUtils.concat;
import static com.yahoo.security.hpke.Constants.HPKE_V1_LABEL;

/**
 * Utilities for labeled KDF expand/extract used by both DHKEM and HPKE
 *
 * @author vekterli
 */
class LabeledKdfUtils {

    /**
     * Section 4 Cryptographic Dependencies:
     *
     * <pre>
     * def LabeledExtract(salt, label, ikm):
     *   labeled_ikm = concat("HPKE-v1", suite_id, label, ikm)
     *   return Extract(salt, labeled_ikm)
     * </pre>
     *
     * We take in the KDF and suite ID explicitly, to allow method reuse between KEM and HPKE.
     */
    static byte[] labeledExtractForSuite(Kdf kdf, byte[] suiteId, byte[] salt, byte[] label, byte[] ikm) {
        byte[] labeledIkm = concat(HPKE_V1_LABEL, suiteId, label, ikm);
        return kdf.extract(salt, labeledIkm);
    }

    /**
     * Section 4 Cryptographic Dependencies:
     *
     * <pre>
     * def LabeledExpand(prk, label, info, L):
     *   labeled_info = concat(I2OSP(L, 2), "HPKE-v1", suite_id,
     *                         label, info)
     *   return Expand(prk, labeled_info, L)
     * </pre>
     *
     * We take in the KDF and suite ID explicitly, to allow method reuse between KEM and HPKE.
     */
    static byte[] labeledExpandForSuite(Kdf kdf, byte[] prk, byte[] suiteId, byte[] label, byte[] info, int nBytesToExpand/*L*/) {
        byte[] labeledInfo = concat(i2osp2((short)nBytesToExpand), HPKE_V1_LABEL, suiteId, label, info);
        return kdf.expand(prk, labeledInfo, nBytesToExpand);
    }

    /**
     * <pre>
     * I2OSP(n, w):
     * Convert non-negative integer n to a w-length, big-endian byte string,
     * as described in [RFC8017].
     * </pre>
     *
     * We provide a simple <code>2OSP(n, 2)</code> specialization since we don't need to
     * encode arbitrary BigIntegers for labels.
     */
    static byte[] i2osp2(short v) {
        return new byte[] { (byte)(v >>> 8), (byte)(v & 0xff) };
    }

}
