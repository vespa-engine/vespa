// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vdslib;

import com.yahoo.document.BucketId;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * @author Simon Thoresen
 */
public class BucketDistributionTestCase {

    private static final int NUM_COLUMNS = 16;
    private static final int MIN_BUCKETBITS = 4;
    private static final int MAX_BUCKETBITS = 9;

    @Test
    public void printExpected() {
        System.out.println("        int[][] expected = new int[][] {");
        for (int numBucketBits = MIN_BUCKETBITS; numBucketBits <= MAX_BUCKETBITS; ++numBucketBits) {
            System.out.print("            new int[] {");
            BucketDistribution bd = new BucketDistribution(NUM_COLUMNS, numBucketBits);
            for (int i = 0; i < bd.getNumBuckets(); ++i) {
                if (i % 32 == 0) {
                    System.out.println("");
                    System.out.print("                ");
                }
                System.out.print(bd.getColumn(new BucketId(16, i)));
                if (i < bd.getNumBuckets() - 1) {
                    System.out.print(", ");
                }
            }
            System.out.print(" }");
            if (numBucketBits < MAX_BUCKETBITS) {
                System.out.print(",");
            }
            System.out.println("");
        }
        System.out.println("        };");
    }

    @Test
    public void testDistribution() {
        int[][] expected = new int[][] {
            new int[] {
                10, 11, 9, 6, 4, 8, 14, 1, 13, 2, 12, 3, 5, 7, 15, 0 },
            new int[] {
                11, 12, 11, 13, 8, 13, 8, 9, 4, 5, 6, 12, 10, 15, 1, 1, 7, 9, 14, 2, 2, 14, 3, 3, 4, 5, 6, 7, 10, 15, 0, 0 },
            new int[] {
                13, 13, 13, 13, 9, 11, 8, 9, 11, 14, 9, 11, 14, 14, 8, 10, 11, 14, 4, 5, 5, 6, 6, 7, 8, 10, 12, 15, 1, 1, 1, 1,
                6, 7, 8, 10, 12, 15, 2, 2, 2, 2, 12, 15, 3, 3, 3, 3, 4, 4, 4, 5, 5, 6, 7, 7, 9, 10, 12, 15, 0, 0, 0, 0 },
            new int[] {
                14, 14, 14, 13, 11, 14, 14, 10, 13, 14, 10, 12, 8, 8, 9, 10, 9, 10, 11, 12, 13, 15, 11, 12, 13, 13, 15, 8, 8, 9, 10, 11,
                11, 12, 13, 15, 4, 4, 5, 5, 5, 5, 15, 6, 6, 7, 7, 7, 8, 9, 9, 10, 11, 12, 14, 15, 1, 1, 1, 1, 1, 1, 1, 1,
                6, 6, 6, 7, 7, 8, 8, 9, 10, 11, 12, 13, 15, 2, 2, 2, 2, 2, 2, 2, 2, 12, 13, 15, 3, 3, 3, 3, 3, 3, 3, 3,
                4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 6, 6, 6, 7, 7, 7, 8, 9, 9, 10, 11, 12, 14, 15, 0, 0, 0, 0, 0, 0, 0, 0 },
            new int[] {
                15, 14, 15, 15, 12, 14, 15, 12, 12, 11, 12, 13, 14, 13, 13, 13, 14, 15, 15, 9, 14, 9, 15, 10, 11, 11, 12, 8, 8, 8, 8, 9,
                9, 10, 10, 10, 11, 11, 12, 13, 13, 14, 10, 11, 11, 12, 13, 13, 14, 13, 13, 14, 15, 7, 8, 8, 8, 9, 9, 9, 10, 10, 10, 11,
                10, 10, 11, 11, 12, 13, 13, 14, 15, 4, 4, 4, 4, 15, 5, 5, 5, 5, 5, 5, 5, 15, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7,
                8, 8, 8, 9, 9, 9, 10, 10, 11, 11, 12, 12, 13, 14, 14, 15, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 8, 8, 8, 9, 9, 9, 10, 10, 11, 11, 12, 12, 13, 14, 14, 15, 2, 2, 2, 2, 2, 2,
                2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 12, 12, 13, 14, 14, 15, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
                4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7,
                8, 8, 8, 9, 9, 9, 10, 10, 11, 11, 12, 12, 13, 14, 15, 15, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 },
            new int[] {
                15, 15, 15, 15, 14, 14, 15, 13, 13, 13, 13, 13, 14, 14, 15, 12, 14, 15, 15, 11, 11, 12, 13, 13, 13, 14, 14, 15, 15, 10, 12, 12,
                12, 13, 13, 13, 14, 14, 15, 15, 9, 9, 9, 10, 10, 11, 11, 11, 12, 12, 12, 12, 13, 13, 14, 15, 15, 8, 8, 8, 8, 9, 9, 9,
                9, 9, 9, 10, 9, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 12, 13, 13, 14, 14, 14, 10, 10, 10, 11, 11, 11, 12, 12, 12, 12,
                13, 13, 14, 14, 14, 15, 15, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 9, 10, 10, 10, 10, 11, 11, 11, 11,
                10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 13, 13, 13, 14, 14, 14, 15, 15, 4, 4, 4, 4, 4, 4, 4, 15, 15, 5, 5, 5, 5, 5,
                5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 14, 14, 14, 15, 15, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 13, 13, 13, 14, 14, 15, 15, 15,
                1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1,
                6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9,
                9, 9, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 13, 13, 13, 14, 14, 14, 15, 15, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
                2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 12, 12, 12, 13, 13, 13, 14, 14, 14, 15, 15,
                3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3, 3,
                4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5,
                5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7, 7, 7, 7, 7, 7, 7, 7,
                8, 8, 8, 8, 8, 8, 8, 9, 9, 9, 9, 9, 10, 10, 10, 10, 10, 11, 11, 11, 11, 12, 12, 12, 13, 13, 13, 14, 14, 15, 15, 15,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 }
        };

        for (int numBucketBits = MIN_BUCKETBITS; numBucketBits <= MAX_BUCKETBITS; ++numBucketBits) {
            BucketDistribution bd = new BucketDistribution(NUM_COLUMNS, numBucketBits);
            assertEquals(NUM_COLUMNS, bd.getNumColumns());
            assertEquals(1 << numBucketBits, bd.getNumBuckets());        
            for (int i = 0; i < bd.getNumBuckets(); ++i) {
                assertEquals(expected[numBucketBits - MIN_BUCKETBITS][i], bd.getColumn(new BucketId(16, i)));
            }
        }
    }

    @Test
    public void testNumBucketBits() {
        Random rnd = new Random();

        BucketDistribution bd = new BucketDistribution(1, 4);
        for (int i = 0; i <= 0xf; ++i) {
            assertEquals(0, bd.getColumn(new BucketId(32, (rnd.nextLong() << 4) & i)));
        }

        bd = new BucketDistribution(1, 8);
        for (int i = 0; i <= 0xff; ++i) {
            assertEquals(0, bd.getColumn(new BucketId(32, (rnd.nextLong() << 8) & i)));
        }

        bd = new BucketDistribution(1, 16);
        for (int i = 0; i <= 0xffff; ++i) {
            assertEquals(0, bd.getColumn(new BucketId(32, (rnd.nextLong() << 16) & i)));
        }
    }

}
