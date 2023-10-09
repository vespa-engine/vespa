// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.concurrent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import java.util.Iterator;

import org.junit.Test;

/**
 * Check we keep the consistent view when reading and writing in parallell.
 *
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ThreadRobustListTestCase {

    private static class Writer implements Runnable {
        private final ThreadRobustList<String> l;
        private final Receiver<Boolean> sharedLock;

        public Writer(final ThreadRobustList<String> l,
                final Receiver<Boolean> sharedLock) {
            this.sharedLock = sharedLock;
            this.l = l;
        }

        @Override
        public void run() {
            for (int i = 0; i < 5; ++i) {
                l.add(String.valueOf(i));
            }
            sharedLock.put(Boolean.TRUE);
            for (int i = 5; i < 100 * 1000; ++i) {
                l.add(String.valueOf(i));
            }
        }

    }

    private static class Reader implements Runnable {
        private final ThreadRobustList<String> l;
        private final Receiver<Boolean> sharedLock;

        public Reader(final ThreadRobustList<String> l,
                final Receiver<Boolean> sharedLock) {
            this.sharedLock = sharedLock;
            this.l = l;
        }

        @Override
        public void run() {
            int n;
            int previous;

            try {
                sharedLock.get(5 * 60 * 1000);
            } catch (final InterruptedException e) {
                fail("Test interrupted.");
            }
            n = countElements();
            assertFalse(n < 5);
            previous = n;
            for (int i = 0; i < 1000; ++i) {
                int reverse = reverseCountElements();
                n = countElements();
                assertFalse(n < reverse);
                assertFalse(n < previous);
                previous = n;
            }
        }

        private int reverseCountElements() {
            int n = 0;
            for (final Iterator<String> j = l.reverseIterator(); j.hasNext(); j.next()) {
                ++n;
            }
            return n;
        }

        private int countElements() {
            int n = 0;
            for (final Iterator<String> j = l.iterator(); j.hasNext(); j.next()) {
                ++n;
            }
            return n;
        }
    }

    @Test
    public final void test() throws InterruptedException {
        final ThreadRobustList<String> container = new ThreadRobustList<>();
        final Receiver<Boolean> lock = new Receiver<>();
        final Reader r = new Reader(container, lock);
        final Writer w = new Writer(container, lock);
        final Thread wt = new Thread(w);
        wt.start();
        r.run();
        wt.join();
    }

}
