// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security.hpke;

import com.yahoo.security.ArrayUtils;

/**
 * Various internal constants used as part of key derivation etc. in HPKE
 *
 * @author vekterli
 */
final class Constants {

    private static byte[] toBytes(String str) {
        return ArrayUtils.toUtf8Bytes(str); // We only expect US-ASCII in practice, so UTF-8 is fine.
    }

    static final byte[] HPKE_V1_LABEL       = toBytes("HPKE-v1");
    static final byte[] EMPTY_LABEL         = new byte[0];
    static final byte[] EAE_PRK_LABEL       = toBytes("eae_prk");
    static final byte[] SHARED_SECRET_LABEL = toBytes("shared_secret");

    /**
     * <pre>
     * default_psk = ""
     * default_psk_id = ""
     * </pre>
     */
    static final byte[] DEFAULT_PSK    = new byte[0];
    static final byte[] DEFAULT_PSK_ID = new byte[0];

    static final byte[] PSK_ID_HASH_LABEL = toBytes("psk_id_hash");
    static final byte[] INFO_HASH_LABEL   = toBytes("info_hash");
    static final byte[] SECRET_LABEL      = toBytes("secret");
    static final byte[] KEY_LABEL         = toBytes("key");
    static final byte[] BASE_NONCE_LABEL  = toBytes("base_nonce");
    static final byte[] EXP_LABEL         = toBytes("exp");

}
