// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.bouncycastle.util.encoders.Hex;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * HKDF tests that ensure that the output of our own implementation matches the test
 * vectors given in <a href="https://tools.ietf.org/html/rfc5869">RFC-5869</a>.
 *
 * We don't expose the internal PRK (pseudo-random key) value of the HKDF itself,
 * so we don't test it explicitly. The actual OKM (output keying material) inherently
 * depends on it, so its correctness is verified transitively.
 *
 * @author vekterli
 */
public class HKDFTest {

    private static byte[] fromHex(String hex) {
        return Hex.decode(hex);
    }

    private static String toHex(byte[] bytes) {
        return Hex.toHexString(bytes);
    }

    /*
       A.1.  Test Case 1

       Basic test case with SHA-256

       Hash = SHA-256
       IKM  = 0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b (22 octets)
       salt = 0x000102030405060708090a0b0c (13 octets)
       info = 0xf0f1f2f3f4f5f6f7f8f9 (10 octets)
       L    = 42

       PRK  = 0x077709362c2e32df0ddc3f0dc47bba63
              90b6c73bb50f9c3122ec844ad7c2b3e5 (32 octets)
       OKM  = 0x3cb25f25faacd57a90434f64d0362f2a
              2d2d0a90cf1a5a4c5db02d56ecc4c5bf
              34007208d5b887185865 (42 octets)
     */
    @Test
    void rfc_5869_test_vector_case_1() {
        var ikm  = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = fromHex("000102030405060708090a0b0c");
        var info = fromHex("f0f1f2f3f4f5f6f7f8f9");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(42, info);
        assertEquals(toHex(okm),
                     "3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                     "34007208d5b887185865");
    }

    @Test
    void rfc_5869_test_vector_case_1_block_boundary_edge_cases() {
        var ikm  = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = fromHex("000102030405060708090a0b0c");
        var info = fromHex("f0f1f2f3f4f5f6f7f8f9");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(31, info); // One less than block size
        assertEquals(toHex(okm),
                     "3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5");

        okm = hkdf.expand(32, info); // Exactly equal to block size
        assertEquals(toHex(okm),
                     "3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf");

        okm = hkdf.expand(33, info); // One more than block size
        assertEquals(toHex(okm),
                     "3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                     "34");
    }

    /*
       A.2.  Test Case 2

       Test with SHA-256 and longer inputs/outputs

       Hash = SHA-256
       IKM  = 0x000102030405060708090a0b0c0d0e0f
              101112131415161718191a1b1c1d1e1f
              202122232425262728292a2b2c2d2e2f
              303132333435363738393a3b3c3d3e3f
              404142434445464748494a4b4c4d4e4f (80 octets)
       salt = 0x606162636465666768696a6b6c6d6e6f
              707172737475767778797a7b7c7d7e7f
              808182838485868788898a8b8c8d8e8f
              909192939495969798999a9b9c9d9e9f
              a0a1a2a3a4a5a6a7a8a9aaabacadaeaf (80 octets)
       info = 0xb0b1b2b3b4b5b6b7b8b9babbbcbdbebf
              c0c1c2c3c4c5c6c7c8c9cacbcccdcecf
              d0d1d2d3d4d5d6d7d8d9dadbdcdddedf
              e0e1e2e3e4e5e6e7e8e9eaebecedeeef
              f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff (80 octets)
       L    = 82

       PRK  = 0x06a6b88c5853361a06104c9ceb35b45c
              ef760014904671014a193f40c15fc244 (32 octets)
       OKM  = 0xb11e398dc80327a1c8e7f78c596a4934
              4f012eda2d4efad8a050cc4c19afa97c
              59045a99cac7827271cb41c65e590e09
              da3275600c2f09b8367793a9aca3db71
              cc30c58179ec3e87c14c01d5c1f3434f
              1d87 (82 octets)
     */
    @Test
    void rfc_5869_test_vector_case_2() {
        var ikm  = fromHex("000102030405060708090a0b0c0d0e0f" +
                           "101112131415161718191a1b1c1d1e1f" +
                           "202122232425262728292a2b2c2d2e2f" +
                           "303132333435363738393a3b3c3d3e3f" +
                           "404142434445464748494a4b4c4d4e4f");
        var salt = fromHex("606162636465666768696a6b6c6d6e6f" +
                           "707172737475767778797a7b7c7d7e7f" +
                           "808182838485868788898a8b8c8d8e8f" +
                           "909192939495969798999a9b9c9d9e9f" +
                           "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
        var info = fromHex("b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                           "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                           "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                           "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                           "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(82, info);
        assertEquals(toHex(okm),
                     "b11e398dc80327a1c8e7f78c596a4934" +
                     "4f012eda2d4efad8a050cc4c19afa97c" +
                     "59045a99cac7827271cb41c65e590e09" +
                     "da3275600c2f09b8367793a9aca3db71" +
                     "cc30c58179ec3e87c14c01d5c1f3434f" +
                     "1d87");
    }

    /*
       A.3.  Test Case 3

       Test with SHA-256 and zero-length salt/info

       Hash = SHA-256
       IKM  = 0x0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b (22 octets)
       salt = (0 octets)
       info = (0 octets)
       L    = 42

       PRK  = 0x19ef24a32c717b167f33a91d6f648bdf
              96596776afdb6377ac434c1c293ccb04 (32 octets)
       OKM  = 0x8da4e775a563c18f715f802a063c5a31
              b8a11f5c5ee1879ec3454e5f3c738d2d
              9d201395faa4b61a96c8 (42 octets)
     */
    @Test
    void rfc_5869_test_vector_case_3() {
        var ikm  = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var info = new byte[0];

        // We don't allow empty salt to the salted factory function, so this is equivalent.
        var hkdf = HKDF.unsaltedExtractedFrom(ikm);
        var okm = hkdf.expand(42, info);
        var expectedKey = "8da4e775a563c18f715f802a063c5a31" +
                          "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                          "9d201395faa4b61a96c8";
        assertEquals(toHex(okm), expectedKey);

        // expand() without explicit context should return as if an empty context array was passed
        okm = hkdf.expand(42);
        assertEquals(toHex(okm), expectedKey);
    }

    @Test
    void requested_key_size_is_bounded_and_checked() {
        var ikm  = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = fromHex("000102030405060708090a0b0c");
        var hkdf = HKDF.extractedFrom(salt, ikm);

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(-1)); // Can't request negative output size

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(0)); // Need at least 1 key byte

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(HKDF.MAX_OUTPUT_SIZE + 1)); // 1 too large

        // However, exactly max should work (though a strange size to request in practice)
        var okm = hkdf.expand(HKDF.MAX_OUTPUT_SIZE);
        assertEquals(okm.length, HKDF.MAX_OUTPUT_SIZE);
    }

    @Test
    void missing_salt_to_salted_factory_function_throws_exception() {
        var ikm  = fromHex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        assertThrows(NullPointerException.class, () -> HKDF.extractedFrom(null, ikm));
        assertThrows(IllegalArgumentException.class, () -> HKDF.extractedFrom(new byte[0], ikm));
    }

    @Test
    void ikm_can_not_be_null_or_empty() {
        var salt = fromHex("000102030405060708090a0b0c");
        assertThrows(NullPointerException.class, () -> HKDF.extractedFrom(salt, null));
        assertThrows(IllegalArgumentException.class, () -> HKDF.extractedFrom(salt, new byte[0]));
        assertThrows(NullPointerException.class, () -> HKDF.unsaltedExtractedFrom(null));
        assertThrows(IllegalArgumentException.class, () -> HKDF.unsaltedExtractedFrom(new byte[0]));
    }

}
