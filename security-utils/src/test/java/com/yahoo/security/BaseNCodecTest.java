// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static com.yahoo.security.ArrayUtils.hex;
import static com.yahoo.security.ArrayUtils.toUtf8Bytes;
import static com.yahoo.security.ArrayUtils.unhex;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * @author vekterli
 */
public class BaseNCodecTest {

    private static void verifyRoundtrip(BaseNCodec codec, byte[] bytes, String expectedEncoded) {
        String enc = codec.encode(bytes);
        assertEquals(expectedEncoded, enc);
        byte[] dec = codec.decode(enc);
        assertEquals(hex(bytes), hex(dec));
    }

    private static void verifyRoundtrip(BaseNCodec codec, String str, String expectedEncoded) {
        verifyRoundtrip(codec, toUtf8Bytes(str), expectedEncoded);
    }

    @Test
    void decoding_chars_not_in_alphabet_throws() {
        var b58 = Base58.codec();
        // [0OIl] are not in Base58 alphabet, but within the alphabet LUT range
        assertThrows(IllegalArgumentException.class, () -> b58.decode("233QC0"));
        // '{' is one beyond 'z', which is the highest char in the LUT range
        assertThrows(IllegalArgumentException.class, () -> b58.decode("233QC{"));
    }

    @Test
    void alphabet_char_duplication_during_codec_setup_throws() {
        assertThrows(IllegalArgumentException.class, () -> BaseNCodec.of("abcda"));
    }

    @Test
    void base58_codec_test_cases_pass() {
        var b58 = Base58.codec();
        assertEquals(58, b58.base());
        // https://datatracker.ietf.org/doc/html/draft-msporny-base58-03 test vectors:
        verifyRoundtrip(b58, "Hello World!", "2NEpo7TZRRrLZSi2U");
        verifyRoundtrip(b58, "The quick brown fox jumps over the lazy dog.",
                "USm3fpXnKG5EUBx2ndxBDMPVciP5hGey2Jh4NDv6gmeo1LkMeiKrLJUUBk6Z");
        verifyRoundtrip(b58, unhex("0000287fb4cd"), "11233QC4");

        // Values that have been cross-referenced with other encoder implementations:
        verifyRoundtrip(b58, "", "");
        verifyRoundtrip(b58, unhex("00"), "1");
        verifyRoundtrip(b58, unhex("0000"), "11");
        verifyRoundtrip(b58, unhex("ff"), "5Q");
        verifyRoundtrip(b58, unhex("00ff"), "15Q");
        verifyRoundtrip(b58, unhex("ff00"), "LQX");
        verifyRoundtrip(b58, unhex("ffffff"), "2UzHL");
        verifyRoundtrip(b58, unhex("287fb4cd"), "233QC4");
    }

    @Test
    void base62_codec_test_cases_pass() {
        var b62 = Base62.codec();
        assertEquals(62, b62.base());
        verifyRoundtrip(b62, "Hello World!", "T8dgcjRGkZ3aysdN");
        verifyRoundtrip(b62, "\0\0Hello World!", "00T8dgcjRGkZ3aysdN");
        verifyRoundtrip(b62, "", "");
        verifyRoundtrip(b62, unhex("00"), "0");
        verifyRoundtrip(b62, unhex("0000"), "00");
        verifyRoundtrip(b62, unhex("00000000ffffffff"), "00004gfFC3");
        verifyRoundtrip(b62, unhex("ffffffff00000000"), "LygHZwPV2MC");
    }

    // Test with some common bases that are easier to reason about:

    @Test
    void codec_generalizes_down_to_base_10() {
        var b10 = BaseNCodec.of("0123456789");
        verifyRoundtrip(b10, unhex("00"), "0");
        verifyRoundtrip(b10, unhex("000f"), "015");
        verifyRoundtrip(b10, unhex("ffff"), "65535");

        // A large prime number: 2^252 + 27742317777372353535851937790883648493 (Curve25519 order)
        var numStr = "7237005577332262213973186563042994240857116359379907606001950938285454250989";
        var numBN = new BigInteger(numStr);
        verifyRoundtrip(b10, numBN.toByteArray(), numStr);
    }

    // Possibly world's most inefficient hex conversion?
    @Test
    void codec_generalizes_down_to_base_16() {
        var b2 = BaseNCodec.of("0123456789ABCDEF");
        assertEquals(16, b2.base());
        verifyRoundtrip(b2, unhex(""), "");
        verifyRoundtrip(b2, unhex("00"), "0");
        verifyRoundtrip(b2, unhex("80"), "80");
        verifyRoundtrip(b2, unhex("01"), "1");
        verifyRoundtrip(b2, unhex("F0"), "F0");
        verifyRoundtrip(b2, unhex("0F"), "F");
        verifyRoundtrip(b2, unhex("F00F"), "F00F");
        verifyRoundtrip(b2, unhex("5FAF"), "5FAF");
    }

    // Very likely genuinely the world's most inefficient binary conversion.
    @Test
    void codec_generalizes_down_to_base_2() {
        var b2 = BaseNCodec.of("01");
        assertEquals(2, b2.base());
        verifyRoundtrip(b2, unhex(""), "");
        verifyRoundtrip(b2, unhex("00"), "0");
        verifyRoundtrip(b2, unhex("000000"), "000"); // note: prefix zero byte sentinels!
        verifyRoundtrip(b2, unhex("80"), "10000000");
        verifyRoundtrip(b2, unhex("01"), "1");
        verifyRoundtrip(b2, unhex("F0"), "11110000");
        verifyRoundtrip(b2, unhex("0F"), "1111");
    }

}
