// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.concurrent;

import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

/**
 * @author mpolden
 */
public class OnceTest {

    @Test(timeout = 60_000)
    public void test_run() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        Once.after(Duration.ZERO, latch::countDown);

        assertTrue(latch.await(30, TimeUnit.SECONDS));
    }

}
