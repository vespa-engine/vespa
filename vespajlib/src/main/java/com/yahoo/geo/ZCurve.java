// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.geo;

/**
 * Contains utility methods for a Z-curve (Morton-order) encoder and
 * decoder.
 *
 * @author gjoranv
 */
public class ZCurve {

    /**
     * Encode two 32 bit integers by bit-interleaving them into one 64 bit
     * integer value. The x-direction owns the least significant bit (bit
     * 0). Both x and y can have negative values.
     *
     * <p>
     * This is a time-efficient implementation. In the first step, the input
     * value is split in two blocks, one containing the most significant bits, and
     * the other containing the least significant bits. The most significant block
     * is then shifted left for as many bits it contains. For each following step
     * every block from the previous step is split in the same manner, with a
     * least and most significant block, and the most significant blocks are shifted
     * left for as many bits they contain (half the number from the previous step).
     * This continues until each block has only one bit.
     *
     * <p>
     * This algorithm works by placing the LSB of all blocks in the correct position
     * after the bit-shifting is done in each step. This algorithm is quite similar
     * to computing the Hamming Weight (or population count) of a bit
     * string, see http://en.wikipedia.org/wiki/Hamming_weight.
     *
     * <p>
     * Efficiency considerations: The encoding operations in this method
     * should require 42 cpu operations, of which many can be executed
     * in parallell. Practical experiments show that one call takes ~15 ns
     * on a 64-bit Intel Xeon processor @2.33GHz, or 35 cycles. This gives
     * an efficiency gain of just ~17% due to the CPUs ability to process
     * parallell instructions, compared to ~50% for the slow method.
     * But still it is 5 times faster.
     *
     * @param x  x value
     * @param y  y value
     * @return  The bit-interleaved long containing x and y.
     */
    public static long encode(int x, int y) {
        long xl = x;
        long yl = y;

        long rx = ((xl & 0x00000000ffff0000L) << 16) | (xl & 0x000000000000ffffL);
        long ry = ((yl & 0x00000000ffff0000L) << 16) | (yl & 0x000000000000ffffL);

        rx = ((rx & 0xff00ff00ff00ff00L) << 8) | (rx & 0x00ff00ff00ff00ffL);
        ry = ((ry & 0xff00ff00ff00ff00L) << 8) | (ry & 0x00ff00ff00ff00ffL);

        rx = ((rx & 0xf0f0f0f0f0f0f0f0L) << 4) | (rx & 0x0f0f0f0f0f0f0f0fL);
        ry = ((ry & 0xf0f0f0f0f0f0f0f0L) << 4) | (ry & 0x0f0f0f0f0f0f0f0fL);

        rx = ((rx & 0xccccccccccccccccL) << 2) | (rx & 0x3333333333333333L);
        ry = ((ry & 0xccccccccccccccccL) << 2) | (ry & 0x3333333333333333L);

        rx = ((rx & 0xaaaaaaaaaaaaaaaaL) << 1) | (rx & 0x5555555555555555L);
        ry = ((ry & 0xaaaaaaaaaaaaaaaaL) << 1) | (ry & 0x5555555555555555L);

        return (rx | (ry << 1));
     }


    /**
     * Decode a z-value into the original two integers.  Returns an
     * array of two Integers, x and y in indices 0 and 1 respectively.
     *
     * @param z  The bit-interleaved long containing x and y.
     * @return   Array of two Integers, x and y.
     */
    public static int[] decode(long z) {
        int[] xy = new int[2];

        long xl = z & 0x5555555555555555L;
        long yl = z & 0xaaaaaaaaaaaaaaaaL;

        xl = ((xl & 0xccccccccccccccccL) >> 1) | (xl & 0x3333333333333333L);
        yl = ((yl & 0xccccccccccccccccL) >> 1) | (yl & 0x3333333333333333L);

        xl = ((xl & 0xf0f0f0f0f0f0f0f0L) >> 2) | (xl & 0x0f0f0f0f0f0f0f0fL);
        yl = ((yl & 0xf0f0f0f0f0f0f0f0L) >> 2) | (yl & 0x0f0f0f0f0f0f0f0fL);

        xl = ((xl & 0xff00ff00ff00ff00L) >> 4) | (xl & 0x00ff00ff00ff00ffL);
        yl = ((yl & 0xff00ff00ff00ff00L) >> 4) | (yl & 0x00ff00ff00ff00ffL);

        xl = ((xl & 0xffff0000ffff0000L) >> 8) | (xl & 0x0000ffff0000ffffL);
        yl = ((yl & 0xffff0000ffff0000L) >> 8) | (yl & 0x0000ffff0000ffffL);

        xl = ((xl & 0xffffffff00000000L) >> 16) | (xl & 0x00000000ffffffffL);
        yl = ((yl & 0xffffffff00000000L) >> 16) | (yl & 0x00000000ffffffffL);

        xy[0] = (int)xl;
        xy[1] = (int)(yl >> 1);
        return xy;
     }



    /**
     * Encode two integers by bit-interleaving them into one Long
     * value.  The x-direction owns the least significant bit (bit
     * 0). Both x and y can have negative values.
     * <br>
     * Efficiency considerations: If Java compiles and runs this code
     * as efficiently as would be the case with a good c-compiler, it
     * should require 5 cpu operations per bit with optimal usage of
     * the CPUs registers on a 64 bit processor(2 bit-shifts, 1 OR, 1
     * AND, and 1 conditional jump for the for-loop). This would
     * correspond to 320+ cycles with no parallell execution.
     * Practical experiments show that one call takes ~75 ns on a
     * 64-bit Intel Xeon processor @2.33GHz, or 175 cycles. This gives
     * an efficiency gain of ~50% due to the CPUs ability to perform
     * several instructions in one clock-cycle. Here, it is probably the
     * bit-shifts that can be done independently of the AND an OR
     * operations, which must be done in sequence.
     *
     * @param x  x value
     * @param y  y value
     * @return the bit-interleaved long containing x and y
     */
    public static long encode_slow(int x, int y) {
        long z = 0L;
        long xl = (long)x;
        long yl = (long)y;

        long mask = 1L;
        for (int i=0; i<32; i++) {
            long bit = (xl << i) & mask;
            z |= bit;
            //System.out.println("xs  "+ i + ": " + toFullBinaryString(xl << i));
            //System.out.println("m   "+ i + ": " + toFullBinaryString(mask));
            //System.out.println("bit "+ i + ": " + toFullBinaryString(bit));
            //System.out.println("z   "+ i + ": " + toFullBinaryString(z));
            mask = mask << 2;
        }

        mask = 2L;
        for (int i=1; i<=32; i++) {
            long bit = (yl << i) & mask;
            z |= bit;
            mask = mask << 2;
        }
        return z;
    }

    /**
     * Decode a z-value into the original two integers.  Returns an
     * array of two Integers, x and y in indices 0 and 1 respectively.
     *
     * @param z  The bit-interleaved long containing x and y.
     * @return   Array of two Integers, x and y.
     */
    public static int[] decode_slow(long z) {
        int[] xy = new int[2];
        long xl = 0L;
        long yl = 0L;

        long mask = 1L;
        for (int i=0; i<32; i++) {
            long bit = (z >> i) & mask;
            xl |= bit;
            //System.out.println("bits : m      lm      lm      lm      lm      lm      lm      lm      l");
            //System.out.println("zs  "+ i + ": " + toFullBinaryString(z >> i));
            //System.out.println("m   "+ i + ": " + toFullBinaryString(mask));
            //System.out.println("bit "+ i + ": " + toFullBinaryString(bit));
            //System.out.println("xl  "+ i + ": " + toFullBinaryString(xl));
            mask = mask << 1;
        }

        mask = 1L;
        for (int i=1; i<=32; i++) {
            long bit = (z >> i) & mask;
            yl |= bit;
            mask = mask << 1;
        }
        xy[0] = (int)xl;
        xy[1] = (int)yl;
        return xy;
    }

    /**
     * Debugging utility that returns a long value as binary string
     * including the leading zeroes.
     */
    public static String toFullBinaryString(long l) {
        StringBuilder s = new StringBuilder(64);
        for (int i=0; i<Long.numberOfLeadingZeros(l); i++) {
            s.append('0');
        }
        if (l == 0) {
            s.deleteCharAt(0);
        }
        s.append(Long.toBinaryString(l));
        return s.toString();
    }

}
