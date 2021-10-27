// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.concurrent;

import org.junit.Test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertEquals;

public class CachedThreadPoolWithFallbackTest {
    private static void countAndBlock(AtomicLong counter, long waitLimit) {
        counter.incrementAndGet();
        try {
            synchronized (counter) {
                while (counter.get() < waitLimit) {
                    counter.wait();
                }
            }
        } catch (InterruptedException e) {}
    }

    @Test
    public void testThatTaskAreQueued() throws InterruptedException {
        CachedThreadPoolWithFallback executor = new CachedThreadPoolWithFallback("test", 1, 30, 1, TimeUnit.SECONDS);
        AtomicLong counter = new AtomicLong(0);
        for (int i = 0; i < 1000; i++) {
            executor.execute(() -> countAndBlock(counter, 100));
        }
        while (counter.get() < 30) {
            Thread.sleep(1);
        }
        Thread.sleep(1);
        assertEquals(30L, counter.get());
        counter.set(100);
        synchronized (counter) {
            counter.notifyAll();
        }
        executor.close();
        assertEquals(1070L, counter.get());
    }
}
