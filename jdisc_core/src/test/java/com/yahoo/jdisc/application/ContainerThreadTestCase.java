// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertSame;


/**
 * @author Simon Thoresen Hult
 */
public class ContainerThreadTestCase {

    @Test
    void requireThatAccessorsWork() {
        MetricConsumer consumer = new MyConsumer();
        ContainerThread thread = new ContainerThread(new MyTask(), consumer);
        assertSame(consumer, thread.consumer());
    }

    @Test
    void requireThatTaskIsRun() throws InterruptedException {
        MyTask task = new MyTask();
        ContainerThread thread = new ContainerThread(task, null);
        thread.start();
        task.latch.await(600, TimeUnit.SECONDS);
    }

    private static class MyConsumer implements MetricConsumer {

        @Override
        public void set(String key, Number val, Metric.Context ctx) {

        }

        @Override
        public void add(String key, Number val, Metric.Context ctx) {

        }

        @Override
        public Metric.Context createContext(Map<String, ?> properties) {
            return null;
        }
    }

    private static class MyTask implements Runnable {

        final CountDownLatch latch = new CountDownLatch(1);

        @Override
        public void run() {
            latch.countDown();
        }
    }
}
