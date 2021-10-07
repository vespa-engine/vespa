// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.http.client.core.operationProcessor;

import com.yahoo.vespa.http.client.core.operationProcessor.ConcurrentDocumentOperationBlocker;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ConcurrentDocumentOperationBlockerTest {

    final ConcurrentDocumentOperationBlocker blocker = new ConcurrentDocumentOperationBlocker();
    final CountDownLatch latch = new CountDownLatch(1);

    @Before
    public void setup() throws InterruptedException {
        blocker.setMaxConcurrency(2);
        blocker.startOperation();
        assertThat(blocker.availablePermits(), is(1));
        blocker.startOperation();
    }

    private void spawnThreadPushOperationThenCountDown() {
        new Thread(() -> {
            try {
                blocker.startOperation();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            latch.countDown();
        }).start();
    }

    @Test
    public void testBasics() throws InterruptedException {
        spawnThreadPushOperationThenCountDown();
        assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
        blocker.operationDone();
        assertTrue(latch.await(120, TimeUnit.SECONDS));
    }

    @Test
    public void testResizeLarger() throws InterruptedException {
        spawnThreadPushOperationThenCountDown();
        assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
        blocker.setMaxConcurrency(3);
        assertTrue(latch.await(120, TimeUnit.SECONDS));
    }

    @Test
    public void testResizeSmaller() throws InterruptedException {
        spawnThreadPushOperationThenCountDown();
        blocker.setMaxConcurrency(1);
        blocker.operationDone();
        assertFalse(latch.await(10, TimeUnit.MILLISECONDS));
        blocker.operationDone();
        assertTrue(latch.await(120, TimeUnit.SECONDS));
    }
}
