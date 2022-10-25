// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.unhex;
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

    private static byte[] sha256DigestOf(byte[]... buffers) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (byte[] buf : buffers) {
                digest.update(buf);
            }
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 should always be present, so this should never be reached in practice
            throw new RuntimeException(e);
        }
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
        var ikm  = unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = unhex("000102030405060708090a0b0c");
        var info = unhex("f0f1f2f3f4f5f6f7f8f9");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(42, info);
        assertEquals("3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                     "34007208d5b887185865",
                     hex(okm));
    }

    @Test
    void rfc_5869_test_vector_case_1_block_boundary_edge_cases() {
        var ikm  = unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = unhex("000102030405060708090a0b0c");
        var info = unhex("f0f1f2f3f4f5f6f7f8f9");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(31, info); // One less than block size
        assertEquals("3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5",
                     hex(okm));

        okm = hkdf.expand(32, info); // Exactly equal to block size
        assertEquals("3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf",
                     hex(okm));

        okm = hkdf.expand(33, info); // One more than block size
        assertEquals("3cb25f25faacd57a90434f64d0362f2a" +
                     "2d2d0a90cf1a5a4c5db02d56ecc4c5bf" +
                     "34",
                     hex(okm));
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
        var ikm  = unhex("000102030405060708090a0b0c0d0e0f" +
                         "101112131415161718191a1b1c1d1e1f" +
                         "202122232425262728292a2b2c2d2e2f" +
                         "303132333435363738393a3b3c3d3e3f" +
                         "404142434445464748494a4b4c4d4e4f");
        var salt = unhex("606162636465666768696a6b6c6d6e6f" +
                         "707172737475767778797a7b7c7d7e7f" +
                         "808182838485868788898a8b8c8d8e8f" +
                         "909192939495969798999a9b9c9d9e9f" +
                         "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf");
        var info = unhex("b0b1b2b3b4b5b6b7b8b9babbbcbdbebf" +
                         "c0c1c2c3c4c5c6c7c8c9cacbcccdcecf" +
                         "d0d1d2d3d4d5d6d7d8d9dadbdcdddedf" +
                         "e0e1e2e3e4e5e6e7e8e9eaebecedeeef" +
                         "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        var okm = hkdf.expand(82, info);
        assertEquals("b11e398dc80327a1c8e7f78c596a4934" +
                     "4f012eda2d4efad8a050cc4c19afa97c" +
                     "59045a99cac7827271cb41c65e590e09" +
                     "da3275600c2f09b8367793a9aca3db71" +
                     "cc30c58179ec3e87c14c01d5c1f3434f" +
                     "1d87",
                     hex(okm));
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
        var ikm  = unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var info = new byte[0];

        // We don't allow empty salt to the salted factory function, so this is equivalent.
        var hkdf = HKDF.unsaltedExtractedFrom(ikm);
        var okm = hkdf.expand(42, info);
        var expectedOkm = "8da4e775a563c18f715f802a063c5a31" +
                          "b8a11f5c5ee1879ec3454e5f3c738d2d" +
                          "9d201395faa4b61a96c8";
        assertEquals(expectedOkm, hex(okm));

        // expand() without explicit context should return as if an empty context array was passed
        okm = hkdf.expand(42);
        assertEquals(expectedOkm, hex(okm));
    }

    @Test
    void requested_key_size_is_bounded_and_checked() {
        var ikm  = unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        var salt = unhex("000102030405060708090a0b0c");
        var hkdf = HKDF.extractedFrom(salt, ikm);

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(-1)); // Can't request negative output size

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(0)); // Need at least 1 key byte

        assertThrows(IllegalArgumentException.class, () -> hkdf.expand(HKDF.MAX_OUTPUT_SIZE + 1)); // 1 too large
    }

    @Test
    void missing_salt_to_salted_factory_function_throws_exception() {
        var ikm  = unhex("0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b0b");
        assertThrows(NullPointerException.class, () -> HKDF.extractedFrom(null, ikm));
        assertThrows(IllegalArgumentException.class, () -> HKDF.extractedFrom(new byte[0], ikm));
    }

    @Test
    void ikm_can_not_be_null_or_empty() {
        var salt = unhex("000102030405060708090a0b0c");
        assertThrows(NullPointerException.class, () -> HKDF.extractedFrom(salt, null));
        assertThrows(IllegalArgumentException.class, () -> HKDF.extractedFrom(salt, new byte[0]));
        assertThrows(NullPointerException.class, () -> HKDF.unsaltedExtractedFrom(null));
        assertThrows(IllegalArgumentException.class, () -> HKDF.unsaltedExtractedFrom(new byte[0]));
    }

    //
    // Subset of Wycheproof test vectors for specific named edge cases
    // From https://github.com/google/wycheproof/blob/master/testvectors/hkdf_sha256_test.json
    //

    @Test
    void maximal_output_size() {
        var ikm  = unhex("bdd9c30b5fab7f22d859db774779b41cc124daf3ce872f6e80951c0edd8f8214");
        var salt = unhex("90983ed74912c6173d0f7cf8164b525361b89bda04d085341a057bde9083b5af");
        var info = unhex("e6483e923d37e4ba");

        var hkdf = HKDF.extractedFrom(salt, ikm);
        assertEquals(8160, HKDF.MAX_OUTPUT_SIZE);
        var okm = hkdf.expand(HKDF.MAX_OUTPUT_SIZE, info);
        // To avoid shoving an 8K sized hex string into the source code, check against the pre-hashed
        // value of the expected OKM output. It's hashes all the way down!
        var expectedOkmSha256Digest = "c17ce0403e133570191dd1d2ca46f6b62623d62e4f0def8de23a51d65d40a009";
        var okmDigest = sha256DigestOf(okm);
        assertEquals(expectedOkmSha256Digest, hex(okmDigest));
    }

    @Test
    void output_collision_for_different_salts() {
        var ikm  = unhex("5943c65bc33bf05a205b04be8ae0ab2e");
        var info = unhex("be082f301a03f87787a80fbea88941214d50c42b");
        var hkdf = HKDF.unsaltedExtractedFrom(ikm);

        var okm = hkdf.expand(32, info);
        var expectedOkm = "e7f384df2eae32addabd068a758dec84ed7fcfd87a5fcceb37b70c51422d7387";
        assertEquals(expectedOkm, hex(okm));

        var salt = unhex("0000000000000000000000000000000000000000000000000000000000000000");
        hkdf = HKDF.extractedFrom(salt, ikm);
        okm = hkdf.expand(32, info);
        assertEquals(expectedOkm, hex(okm));
    }

    @Test
    void salt_longer_than_block_size_is_equivalent_to_hash_of_the_salt() {
        var ikm  = unhex("624a5b59c2be55cbe29ea90c0020a7e8c60f2501");
        var info = unhex("5447e595250d02165aae3e61fa90313e25509a7b");
        var salts = List.of("c737d7278df1ec7c0a549ce964abd51c3df1d3584d49e77208cd3f9f5bbfb32e",
                            "1a08959149f4b073bcd902c9bc4ed0324c21c95590773afc77037d610b9584806aeeeda8b5" +
                                    "d588d0cd79e7c12211b8e394067516ce12946d61111a52042b539353");
        var expectedOkm = "d45c3909269f4b5f9de1fb2eeb0593a7cb9175c8835aba37e0ee0c4cb3bd87c4";
        for (var salt : salts) {
            var hkdf = HKDF.extractedFrom(unhex(salt), ikm);
            var okm = hkdf.expand(32, info);
            assertEquals(expectedOkm, hex(okm));
        }
    }

    @Test
    void salt_shorter_than_the_block_size_is_padded_with_zeros() {
        var ikm  = unhex("5943c65bc33bf05a205b04be8ae0ab2e");
        var info = unhex("be082f301a03f87787a80fbea88941214d50c42b");
        var expectedOkm = "43e371354001617abb70454751059625ef1a64e0f818469c2f886b27140a0166";
        var salts = List.of("e69dcaad55fb0536",
                            "e69dcaad55fb05360000000000000000",
                            "e69dcaad55fb053600000000000000000000000000000000",
                            "e69dcaad55fb0536000000000000000000000000000000000000000000000000",
                            "e69dcaad55fb05360000000000000000000000000000000000000000000000000000000000000000",
                            "e69dcaad55fb053600000000000000000000000000000000000000000000000000000000000000000000000000000000",
                            "e69dcaad55fb0536000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000");
        for (var salt : salts) {
            var hkdf = HKDF.extractedFrom(unhex(salt), ikm);
            var okm = hkdf.expand(32, info);
            assertEquals(expectedOkm, hex(okm), "Failed for salt %s".formatted(salt));
        }
    }

}
