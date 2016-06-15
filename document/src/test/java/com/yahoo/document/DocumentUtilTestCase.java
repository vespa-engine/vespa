// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.document;

/**
 * @author <a href="mailto:einarmr@yahoo-inc.com">Einar M R Rosenvinge</a>
 * @since 5.1.10
 */
public class DocumentUtilTestCase extends junit.framework.TestCase {

    public void testBasic() {
/***
        final long heapBytes = Runtime.getRuntime().maxMemory();
        long maxPendingBytes = DocumentUtil.calculateMaxPendingSize(1.0, 1.0, 0);
        almostEquals(heapBytes / 5, maxPendingBytes, 512*1024);
    }

    public void test2_2() {
        final long heapBytes = Runtime.getRuntime().maxMemory();
        long maxPendingBytes = DocumentUtil.calculateMaxPendingSize(2.0, 2.0, 0);
        almostEquals(heapBytes / 5, maxPendingBytes, 512*1024);
    }

    public void test4_4() {
        final long heapBytes = Runtime.getRuntime().maxMemory();
        long maxPendingBytes = DocumentUtil.calculateMaxPendingSize(4.0, 4.0, 0);
        almostEquals(heapBytes / 17, maxPendingBytes, 512*1024);
    }

    public void test8_8() {
        final long heapBytes = Runtime.getRuntime().maxMemory();
        long maxPendingBytes = DocumentUtil.calculateMaxPendingSize(8.0, 8.0, 0);
        almostEquals(heapBytes / 65, maxPendingBytes, 512*1024);
    }

    public void test10000_10000() {
        long maxPendingBytes = DocumentUtil.calculateMaxPendingSize(10000.0, 10000.0, 0);
        almostEquals(1*1024*1024, maxPendingBytes, 512*1024);
***/
    }

    private static void almostEquals(long expected, long actual, long off) {
        System.err.println("Got actual " + (((double) actual) / 1024d / 1024d) + " MB, expected "
                + (((double) expected) / 1024d / 1024d) + " MB, within +/- " + (((double) off) / 1024d / 1024d) + " MB");

        assertTrue(actual > (expected - off) && actual < (expected + off));
    }
}
