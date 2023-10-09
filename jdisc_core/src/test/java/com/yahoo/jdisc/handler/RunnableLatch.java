// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.handler;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Simon Thoresen Hult
 */
class RunnableLatch implements Runnable {

    private final CountDownLatch latch = new CountDownLatch(1);

    @Override
    public void run() {
        latch.countDown();
    }

    public boolean await(long timeout, TimeUnit unit) throws InterruptedException {
        return latch.await(timeout, unit);
    }
}
