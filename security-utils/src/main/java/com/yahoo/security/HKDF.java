// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;

/**
 * Implementation of RFC-5869 HMAC-based Extract-and-Expand Key Derivation Function (HKDF).
 *
 * <p>The HKDF is initialized ("extracted") from a (non-secret) salt and a secret key.
 * From this, any number of secret keys can be derived ("expanded") deterministically.</p>
 *
 * <p>When multiple keys are to be derived from the same initial keying/salting material,
 * each separate key should use a distinct "context" in the {@link #expand(int, byte[])}
 * call. This ensures that there exists a domain separation between the keys.
 * Using the same context as another key on a HKDF initialized with the same salt+key
 * results in the exact same derived key material as that key.</p>
 *
 * <p>This implementation only offers HMAC-SHA256-based key derivation.</p>
 *
 * @see <a href="https://tools.ietf.org/html/rfc5869">RFC-5869</a>
 * @see <a href="https://en.wikipedia.org/wiki/HKDF">HKDF on Wikipedia</a>
 *
 * @author vekterli
 */
public final class HKDF {

    private static final int    HASH_LEN        = 32; // Fixed output size of HMAC-SHA256. Corresponds to HashLen in the spec
    private static final byte[] EMPTY_BYTES     = new byte[0];
    private static final byte[] ALL_ZEROS_SALT  = new byte[HASH_LEN];
    public static final int     MAX_OUTPUT_SIZE = 255 * HASH_LEN;

    private final byte[] pseudoRandomKey; // Corresponds to "PRK" in spec

    private HKDF(byte[] pseudoRandomKey) {
        this.pseudoRandomKey = pseudoRandomKey;
    }

    private static Mac createHmacSha256() {
        try {
            return Mac.getInstance("HmacSHA256");
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return the computed pseudo-random key (PRK) used as input for each <code>expand()</code> call.
     */
    public byte[] pseudoRandomKey() {
        return this.pseudoRandomKey;
    }

    /**
     * @return a new HKDF instance initially keyed with the given PRK
     */
    public static HKDF ofPseudoRandomKey(byte[] prk) {
        return new HKDF(prk);
    }

    private static SecretKeySpec hmacKeyFrom(byte[] rawKey) {
        return new SecretKeySpec(rawKey, "HmacSHA256");
    }

    private static Mac createKeyedHmacSha256(byte[] rawKey) {
        var hmac = createHmacSha256();
        try {
            hmac.init(hmacKeyFrom(rawKey));
        } catch (InvalidKeyException e) {
            throw new RuntimeException(e);
        }
        return hmac;
    }

    private static void validateExtractionParams(byte[] salt, byte[] ikm) {
        Objects.requireNonNull(salt);
        Objects.requireNonNull(ikm);
        if (ikm.length == 0) {
            throw new IllegalArgumentException("HKDF extraction IKM array can not be empty");
        }
        if (salt.length == 0) {
            throw new IllegalArgumentException("HKDF extraction salt array can not be empty");
        }
    }

    /**
     * Creates and returns a new HKDF instance extracted from the given salt and key.
     *
     * <p>Both the salt and input key value may be of arbitrary size, but it is recommended
     * to have both be at least 16 bytes in size.</p>
     *
     * @param salt a non-secret salt value. Should ideally be high entropy and functionally
     *             "as if random". May not be empty, use {@link #unsaltedExtractedFrom(byte[])}
     *             if unsalted extraction is desired (though this is not recommended).
     * @param ikm secret initial Input Keying Material value.
     * @return a new HDFK instance ready for deriving keys based on the salt and IKM.
     */
    public static HKDF extractedFrom(byte[] salt, byte[] ikm) {
        validateExtractionParams(salt, ikm);
        /*
          RFC-5869, Step 2.2, Extract:

          HKDF-Extract(salt, IKM) -> PRK

          Options:
             Hash     a hash function; HashLen denotes the length of the
                      hash function output in octets

          Inputs:
             salt     optional salt value (a non-secret random value);
                      if not provided, it is set to a string of HashLen zeros.
             IKM      input keying material

          Output:
             PRK      a pseudorandom key (of HashLen octets)

          The output PRK is calculated as follows:

          PRK = HMAC-Hash(salt, IKM)
        */
        var mac = createKeyedHmacSha256(salt); // Note: HDFK is initially keyed on the salt, _not_ on ikm!
        mac.update(ikm);
        return new HKDF(/*PRK = */ mac.doFinal());
    }

    /**
     * Creates and returns a new <em>unsalted</em> HKDF instance extracted from the given key.
     *
     * <p>Prefer using the salted {@link #extractedFrom(byte[], byte[])} method if possible.</p>
     *
     * @param ikm secret initial Input Keying Material value.
     * @return a new HDFK instance ready for deriving keys based on the IKM and an all-zero salt.
     */
    public static HKDF unsaltedExtractedFrom(byte[] ikm) {
        return extractedFrom(ALL_ZEROS_SALT, ikm);
    }

    /**
     * Derives a key with a given number of bytes for a particular context. The returned
     * key is always deterministic for a given unique context and a HKDF initialized with
     * a specific salt+IKM pair.
     *
     * <p>Thread safety: multiple threads can safely call <code>expand()</code> simultaneously
     * on the same HKDF object.</p>
     *
     * @param wantedBytes Positive number of output bytes. Must be less than or equal to {@link #MAX_OUTPUT_SIZE}
     * @param context Context for key derivation. Derivation is deterministic for a given context.
     *                Note: this maps to the "info" field in RFC-5869.
     * @return A byte buffer of size wantedBytes filled with derived key material
     */
    public byte[] expand(int wantedBytes, byte[] context) {
        Objects.requireNonNull(context);
        verifyWantedBytesWithinBounds(wantedBytes);
        return expandImpl(wantedBytes, context);
    }

    /**
     * Derives a key with a given number of bytes. The returned key is always deterministic
     * for a HKDF initialized with a specific salt+IKM pair.
     *
     * <p>If more than one key is to be derived, use {@link #expand(int, byte[])}</p>
     *
     * <p>Thread safety: multiple threads can safely call <code>expand()</code> simultaneously
     * on the same HKDF object.</p>
     *
     * @param wantedBytes Positive number of output bytes. Must be less than or equal to {@link #MAX_OUTPUT_SIZE}
     * @return A byte buffer of size wantedBytes filled with derived key material
     */
    public byte[] expand(int wantedBytes) {
        return expand(wantedBytes, EMPTY_BYTES);
    }

    private void verifyWantedBytesWithinBounds(int wantedBytes) {
        if (wantedBytes <= 0) {
            throw new IllegalArgumentException("Requested negative or zero number of HKDF output bytes");
        }
        if (wantedBytes > MAX_OUTPUT_SIZE) {
            throw new IllegalArgumentException("Too many requested HKDF output bytes (max %d, got %d)"
                                               .formatted(MAX_OUTPUT_SIZE, wantedBytes));
        }
    }

    private byte[] expandImpl(int wantedBytes, byte[] context) {
        /*
           RFC-5869, Step 2.3, Expand:

           HKDF-Expand(PRK, info, L) -> OKM

           Inputs:
              PRK      a pseudorandom key of at least HashLen octets
                       (usually, the output from the extract step)
              info     optional context and application specific information
                       (can be a zero-length string)
              L        length of output keying material in octets
                       (<= 255*HashLen)

           Output:
              OKM      output keying material (of L octets)

           The output OKM is calculated as follows:

           N = ceil(L/HashLen)
           T = T(1) | T(2) | T(3) | ... | T(N)
           OKM = first L octets of T

           where:
           T(0) = empty string (zero length)
           T(1) = HMAC-Hash(PRK, T(0) | info | 0x01)
           T(2) = HMAC-Hash(PRK, T(1) | info | 0x02)
           T(3) = HMAC-Hash(PRK, T(2) | info | 0x03)
           ...
        */
        var prkHmac = createKeyedHmacSha256(pseudoRandomKey);
        int blocks  = (wantedBytes / HASH_LEN) + ((wantedBytes % HASH_LEN) != 0 ? 1 : 0); // N
        var buffer  = ByteBuffer.allocate(blocks * HASH_LEN); // T
        byte[] lastBlock = EMPTY_BYTES; // initially T(0)
        for (int i = 0; i < blocks; ++i) {
            prkHmac.update(lastBlock);
            prkHmac.update(context);
            prkHmac.update((byte)(i + 1)); // Number of blocks shall never exceed 255
            // HMAC instance can be reused across doFinal() calls; resets back to initially keyed state.
            lastBlock = prkHmac.doFinal();
            buffer.put(lastBlock);
        }
        buffer.flip();
        byte[] outputKeyingMaterial = new byte[wantedBytes]; // OKM
        buffer.get(outputKeyingMaterial);
        return outputKeyingMaterial;
    }

}
