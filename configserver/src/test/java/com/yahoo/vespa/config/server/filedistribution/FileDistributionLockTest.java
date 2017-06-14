// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.filedistribution;

import com.yahoo.vespa.config.server.TestWithCurator;
import com.yahoo.vespa.curator.recipes.CuratorLockException;
import com.yahoo.vespa.curator.mock.MockCurator;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;

/**
 * @author lulf
 */
public class FileDistributionLockTest extends TestWithCurator {

    FileDistributionLock lock;
    private int value = 0;

    @Before
    public void setupLock() {
        lock = new FileDistributionLock(curator, "/lock");
        value = 0;
    }

    @Test
    public void testDistributedLock() throws InterruptedException, TimeoutException, ExecutionException {
        ExecutorService executor = Executors.newFixedThreadPool(20);

        List<Future<?>> futureList = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            futureList.add(executor.submit(() -> {
                lock.lock();
                value++;
                lock.unlock();
            }));
        }

        for (Future<?> future : futureList) {
            future.get(600, TimeUnit.SECONDS);
        }
        assertThat(value, is(20));
    }

    @Test
    public void testDistributedTryLockFailure() throws InterruptedException {
        MockCurator mockCurator = new MockCurator();
        lock = new FileDistributionLock(mockCurator, "/mocklock");
        mockCurator.timeoutOnLock = true;
        assertFalse(lock.tryLock(600, TimeUnit.SECONDS));
        mockCurator.timeoutOnLock = false;
        // Second time should not be blocking
        Thread t = new Thread(() -> {
            try {
                if (lock.tryLock(6, TimeUnit.SECONDS)) {
                    value = 1;
                    lock.unlock();
                }
            } catch (InterruptedException e) {
            }
        });
        assertThat(value, is(0));
        t.start();
        t.join();
        assertThat(value, is(1));
    }

    @Test
    public void testDistributedLockExceptionFailure() throws InterruptedException {
        MockCurator mockCurator = new MockCurator();
        lock = new FileDistributionLock(mockCurator, "/mocklock");
        mockCurator.throwExceptionOnLock = true;
        try {
            lock.lock();
            fail("Lock call should not succeed");
        } catch (CuratorLockException e) {
            // ignore
        }
        mockCurator.throwExceptionOnLock = false;
        // Second time should not be blocking
        Thread t = new Thread(() -> {
            try {
                lock.lock();
                value = 1;
                lock.unlock();
            } catch (Exception e) {
                fail("Should not fail");
            }
        });
        assertThat(value, is(0));
        t.start();
        t.join();
        assertThat(value, is(1));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConditionNotSupported() {
        lock.newCondition();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testLockInterruptiblyNotSupported() throws InterruptedException {
        lock.lockInterruptibly();
    }
}
