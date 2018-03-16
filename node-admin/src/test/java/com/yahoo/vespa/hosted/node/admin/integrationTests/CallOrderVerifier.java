// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import java.util.Iterator;
import java.util.LinkedList;

import static org.junit.Assert.assertTrue;

/**
 * Takes in strings representing function calls with their parameters and allows to check whether a subset of calls
 * occurred in a specific order. For example, by calling {@link CallOrderVerifier#add(String)}
 * with A, B, C, D, E, D, A, F, D and G, then {@link CallOrderVerifier#verifyInOrder(String...)} with
 * A, B, D => true,
 * A, D, A => true,
 * C, D, F => true,
 * B, D, A => true
 * B, F, D, A => false,
 * C, B => false
 *
 * @author freva
 */
public class CallOrderVerifier {
    private static final int waitForCallOrderTimeout = 600; //ms

    private final LinkedList<String> callOrder = new LinkedList<>();
    private final Object monitor = new Object();

    public void add(String call) {
        synchronized (monitor) {
            callOrder.add(call);
        }
    }

    public void assertInOrder(String... functionCalls) {
        assertInOrder(waitForCallOrderTimeout, functionCalls);
    }

    public void assertInOrder(long timeout, String... functionCalls) {
        assertInOrderWithAssertMessage(timeout, "", functionCalls);
    }

    public void assertInOrderWithAssertMessage(String assertMessage, String... functionCalls) {
        assertInOrderWithAssertMessage(waitForCallOrderTimeout, assertMessage, functionCalls);
    }

    public void assertInOrderWithAssertMessage(long timeout, String assertMessage, String... functionCalls) {
        boolean inOrder = verifyInOrder(timeout, functionCalls);
        if ( ! inOrder && ! assertMessage.isEmpty())
            System.err.println(assertMessage);
        assertTrue(toString(), inOrder);
    }

    /**
     * Checks if list of function calls occur in order given within a timeout
     * @param timeout Max number of milliseconds to check for if function calls occur in order
     * @param functionCalls The expected order of function calls
     * @return true if the actual order of calls was equal to the order provided within timeout, false otherwise.
     */
    private boolean verifyInOrder(long timeout, String... functionCalls) {
        final long startTime = System.currentTimeMillis();
        while (System.currentTimeMillis() - startTime < timeout) {
            if (verifyInOrder(functionCalls)) {
                return true;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return false;
    }

    private boolean verifyInOrder(String... functionCalls) {
        int pos = 0;
        synchronized (monitor) {
            for (String functionCall : functionCalls) {
                int temp = indexOf(callOrder.listIterator(pos), functionCall);
                if (temp == -1) {
                    return false;
                }
                pos += temp;
            }
        }

        return true;
    }

    /**
     * Finds the first index of needle in haystack after a given position.
     * @param iter Iterator to search in
     * @param search Element to find in iterator
     * @return Index of the next search in  after startPos, -1 if not found
     */
    private int indexOf(Iterator<String> iter, String search) {
        for (int i = 0; iter.hasNext(); i++) {
            if (search.equals(iter.next())) {
                return i;
            }
        }

        return -1;
    }

    @Override
    public String toString() {
        synchronized (monitor) {
            return callOrder.toString();
        }
    }
}
