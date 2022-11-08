// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.security;

import java.math.BigInteger;
import java.util.Arrays;

/**
 * <p>
 * Codec that enables easy conversion from an array of bytes to any numeric base in [2, 256)
 * and back again, using a supplied custom alphabet.
 * </p>
 * <p>
 * Implemented by treating the input byte sequence to encode verbatim as a big-endian
 * <code>BigInteger</code> and iteratively doing a <code>divmod</code> operation until
 * the quotient is zero, emitting the modulus mapped onto the alphabet for each iteration.
 * </p>
 * <p>
 * Decoding reverses this process, ending up with the same <code>BigInteger</code> as in
 * the initial encoding step.
 * </p>
 * <p>
 * Note that <code>BigInteger</code>s represent the <em>canonical</em> form of any given
 * integer, which means that leading zero bytes are implicitly ignored. We therefore
 * special-case this by unary-coding the number of leading zeroes in the encoded form,
 * where a leading zero byte is mapped to the <em>first</em> character of the alphabet.
 * </p>
 * <p>Example for Base58, which starts its alphabet with 1 (0 is not present):</p>
 * <pre>
 *   "Hello World!"     = "2NEpo7TZRRrLZSi2U"
 *   "\0\0Hello World!" = "112NEpo7TZRRrLZSi2U" (note leading 1s)
 * </pre>
 * <p>Example for Base62, which starts its alphabet with 0:</p>
 * <pre>
 *   "Hello World!"     = "T8dgcjRGkZ3aysdN"
 *   "\0\0Hello World!" = "00T8dgcjRGkZ3aysdN" (node leading 0s)
 * </pre>
 * <p>
 * <strong>Important:</strong> runtime complexity is <em>O(n<sup>2</sup>)</em> for both
 * encoding and decoding, so this should only be used to encode/decode relatively short
 * byte sequences. This is <em>not</em> a replacement for Base64 etc. encoding that runs
 * in linear time! In addition, a <code>BaseNCodec</code> with a Base64 alphabet encodes
 * to a completely different output than a regular Base64 encoder when the input is not
 * evenly divisible by three. This is due to regular Base64 explicitly handling padding,
 * while this codec does not.
 * </p>
 *
 * @author vekterli
 */
public class BaseNCodec {

    public static final int MAX_BASE = 255; /** Inclusive */

    private static class Alphabet {
        final char[] alphabetChars;
        final int[]  reverseLut;

        Alphabet(String alphabetIn) {
            if (alphabetIn.length() < 2) { // We don't do unary...
                throw new IllegalArgumentException("Alphabet requires at least two symbols");
            }
            if (alphabetIn.length() > MAX_BASE) {
                throw new IllegalArgumentException("Alphabet size too large");
            }
            alphabetChars = alphabetIn.toCharArray();
            int highestChar = Integer.MIN_VALUE;
            for (char ch : alphabetChars) {
                highestChar = Math.max(highestChar, ch);
            }
            reverseLut = new int[highestChar + 1];
            Arrays.fill(reverseLut, -1); // -1 => invalid mapping
            for (int i = 0; i < alphabetChars.length; ++i) {
                if (reverseLut[alphabetChars[i]] != -1) {
                    throw new IllegalArgumentException("Alphabet character '%c' occurs more than once"
                                                       .formatted(alphabetChars[i]));
                }
                reverseLut[alphabetChars[i]] = i;
            }
        }
    }

    private static final BigInteger BN_ZERO = BigInteger.valueOf(0);

    private final Alphabet   alphabet;
    private final BigInteger alphabetLenBN;

    private BaseNCodec(String alphabet) {
        this.alphabet = new Alphabet(alphabet);
        this.alphabetLenBN = BigInteger.valueOf(this.alphabet.alphabetChars.length);
    }

    public static BaseNCodec of(String alphabet) {
        return new BaseNCodec(alphabet);
    }

    public int base() { return this.alphabet.alphabetChars.length; }

    public String encode(byte[] input) {
        var sb  = new StringBuilder(input.length * 2); // Not at all exact, but builder can resize anyway
        var num = new BigInteger(1, input); // Treat as _positive_ big endian bigint (explicit signum=1)
        // Standard base N digit conversion loop. Note: emits in reverse order since we
        // append the least significant digit first. We reverse this later on.
        while (!num.equals(BN_ZERO)) {
            BigInteger[] quotRem = num.divideAndRemainder(alphabetLenBN);
            num = quotRem[0];
            sb.append(alphabet.alphabetChars[quotRem[1].intValue()]);
        }
        for (byte leadingByte : input) {
            if (leadingByte != 0x00) {
                break;
            }
            sb.append(alphabet.alphabetChars[0]);
        }
        return sb.reverse().toString();
    }

    public byte[] decode(String input) {
        char[] inputChars = input.toCharArray();
        int prefixNulls = 0;
        for (char leadingChar : inputChars) {
            if (leadingChar != alphabet.alphabetChars[0]) {
                break;
            }
            ++prefixNulls;
        }
        // Restore the BigInteger representation by reversing the base conversion done during encoding.
        var accu = BN_ZERO;
        for (char c : inputChars) {
            int idx = (c < alphabet.reverseLut.length) ? alphabet.reverseLut[c] : -1;
            if (idx == -1) {
                throw new IllegalArgumentException("Input character not part of codec alphabet");
            }
            accu = accu.multiply(alphabetLenBN).add(BigInteger.valueOf(idx));
        }
        byte[] bnBytes = accu.toByteArray();
        // If the most significant bigint byte is zero, it means the most significant bit of the
        // next byte is 1 (or the bnBytes length is 1, in which case prefixNulls == 1) and the bigint
        // representation uses 1 extra byte to be positive in 2's complement. If so, prune it away
        // to avoid prefixing with a spurious null-byte.
        boolean msbZero = (bnBytes[0] == 0x0);
        if (prefixNulls == 0 && !msbZero) {
            return bnBytes;
        } else {
            int realLen = (msbZero ? bnBytes.length - 1 : bnBytes.length);
            byte[] result = new byte[prefixNulls + realLen];
            // #prefixNulls prefix bytes are implicitly zero
            System.arraycopy(bnBytes, (msbZero ? 1 : 0), result, prefixNulls, realLen);
            return result;
        }
    }

}
