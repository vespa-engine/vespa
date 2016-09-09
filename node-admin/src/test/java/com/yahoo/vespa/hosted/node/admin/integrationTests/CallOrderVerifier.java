// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.node.admin.integrationTests;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

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
 * @author valerijf
 */
public class CallOrderVerifier {
    private final LinkedList<String> callOrder = new LinkedList<>();
    private final Object monitor = new Object();

    public void add(String call) {
        synchronized (monitor) {
            callOrder.add(call);
        }
    }


    /**
     * Checks if list of function calls occur in order given within a timeout
     * @param timeout Max number of milliseconds to check for if function calls occur in order
     * @param functionCalls The expected order of function calls
     * @return true if the actual order of calls was equal to the order provided within timeout, false otherwise.
     */
    public boolean verifyInOrder(long timeout, String... functionCalls) {
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
                int temp = indexOf(callOrder, functionCall, pos);
                if (temp < pos) {
                    return false;
                }
                pos = temp;
            }
        }

        return true;
    }

    /**
     * Finds the first index of needle in haystack after a given position.
     * @param haystack List to search for an element in
     * @param needle Element to find in list
     * @param startPos Index to start search from
     * @return Index of the next needle in haystack after startPos, -1 if not found
     */
    private int indexOf(List<String> haystack, String needle, int startPos) {
        synchronized (monitor) {
            Iterator<String> iter = haystack.listIterator(startPos);
            for (int i = startPos; iter.hasNext(); i++) {
                if (needle.equals(iter.next())) {
                    return i;
                }
            }
        }

        return -1;
    }
}
