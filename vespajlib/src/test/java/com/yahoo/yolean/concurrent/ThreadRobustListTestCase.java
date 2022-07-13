// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.yolean.concurrent;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class ThreadRobustListTestCase {

    private final static int NUM_THREADS = 64;
    private final static int NUM_ITEMS_TO_WRITE = 1000000;
    private final static int NUM_TIMES_TO_READ = 10;

    @Test
    public void requireThatListIsThreadRobust() throws Exception {
        final CountDownLatch latch = new CountDownLatch(NUM_THREADS);
        final ThreadRobustList<Integer> sharedList = new ThreadRobustList<>();

        List<Callable<Boolean>> tasks = new ArrayList<>(NUM_THREADS);
        tasks.add(new WriterTask(latch, sharedList));
        for (int i = 1; i < NUM_THREADS; ++i) {
            tasks.add(new ReaderTask(latch, sharedList));
        }
        for (Future<Boolean> result : Executors.newFixedThreadPool(NUM_THREADS).invokeAll(tasks)) {
            assertTrue(result.get(60, TimeUnit.SECONDS));
        }
    }

    @Test
    public void requireThatAccessorsWork() {
        ThreadRobustList<Object> lst = new ThreadRobustList<>();
        assertTrue(lst.isEmpty());
        assertFalse(lst.iterator().hasNext());

        Object foo = new Object();
        lst.add(foo);
        assertFalse(lst.isEmpty());
        Iterator<Object> it = lst.iterator();
        assertNotNull(it);
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertFalse(it.hasNext());

        Object bar = new Object();
        lst.add(bar);
        assertFalse(lst.isEmpty());
        assertNotNull(it = lst.iterator());
        assertTrue(it.hasNext());
        assertSame(foo, it.next());
        assertTrue(it.hasNext());
        assertSame(bar, it.next());
        assertFalse(it.hasNext());
    }

    @Test
    public void requireThatIteratorNextThrowsNoSuchElementExceptionWhenDone() {
        ThreadRobustList<Object> lst = new ThreadRobustList<>();
        Iterator<Object> it = lst.iterator();
        assertFalse(it.hasNext());
        try {
            it.next();
            fail();
        } catch (NoSuchElementException e) {

        }
    }

    @Test
    public void requireThatIteratorRemoveIsNotSupported() {
        ThreadRobustList<Object> lst = new ThreadRobustList<>();
        Object obj = new Object();
        lst.add(obj);
        Iterator<Object> it = lst.iterator();
        assertTrue(it.hasNext());
        assertSame(obj, it.next());
        try {
            it.remove();
            fail();
        } catch (UnsupportedOperationException e) {

        }
    }

    private static class WriterTask implements Callable<Boolean> {

        final CountDownLatch latch;
        final ThreadRobustList<Integer> sharedList;

        WriterTask(CountDownLatch latch, ThreadRobustList<Integer> sharedList) {
            this.latch = latch;
            this.sharedList = sharedList;
        }

        @Override
        public Boolean call() throws Exception {
            latch.countDown();
            assertTrue(latch.await(60, TimeUnit.SECONDS));
            for (int i = 0; i < NUM_ITEMS_TO_WRITE; ++i) {
                sharedList.add(i);
            }
            return true;
        }
    }

    private static class ReaderTask implements Callable<Boolean> {

        final CountDownLatch latch;
        final ThreadRobustList<Integer> sharedList;

        ReaderTask(CountDownLatch latch, ThreadRobustList<Integer> sharedList) {
            this.latch = latch;
            this.sharedList = sharedList;
        }

        @Override
        public Boolean call() throws Exception {
            latch.countDown();
            assertTrue(latch.await(60, TimeUnit.SECONDS));
            for (int i = 0; i < NUM_TIMES_TO_READ; ++i) {
                Iterator<Integer> it = sharedList.iterator();
                for (int j = 0; it.hasNext(); ++j) {
                    assertEquals(j, it.next().intValue());
                }
            }
            return true;
        }
    }
}
