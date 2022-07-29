// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.application;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.yahoo.jdisc.Metric;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * @author Simon Thoresen Hult
 */
public class MetricImplTestCase {

    @Test
    void requireThatClassIsInjectedByDefault() {
        Metric metric = Guice.createInjector().getInstance(Metric.class);
        assertTrue(metric instanceof MetricImpl);
    }

    @Test
    void requireThatConsumerIsOptional() {
        Injector injector = Guice.createInjector();
        Metric metric = injector.getInstance(Metric.class);
        metric.set("foo", 6, null);
        metric.add("foo", 9, null);
    }

    @Test
    void requireThatConsumerIsCalled() throws InterruptedException {
        final MyConsumer consumer = new MyConsumer();
        Injector injector = Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                bind(MetricConsumer.class).toInstance(consumer);
            }
        });
        Metric metric = injector.getInstance(Metric.class);
        metric.set("foo", 6, null);
        assertEquals(6, consumer.map.get("foo").intValue());
        metric.add("foo", 9, null);
        assertEquals(15, consumer.map.get("foo").intValue());
        Metric.Context ctx = metric.createContext(null);
        assertEquals(consumer.ctx, ctx);
    }

    @Test
    void requireThatWorkerMetricHasPrecedence() throws InterruptedException {
        final MyConsumer globalConsumer = new MyConsumer();
        Injector injector = Guice.createInjector(new AbstractModule() {

            @Override
            protected void configure() {
                bind(MetricConsumer.class).toInstance(globalConsumer);
            }
        });
        Metric metric = injector.getInstance(Metric.class);

        MyConsumer localConsumer = new MyConsumer();
        localConsumer.latchRef.set(new CountDownLatch(1));
        new ContainerThread(new SetTask(metric, "foo", 6), localConsumer).start();
        localConsumer.latchRef.get().await(600, TimeUnit.SECONDS);
        assertEquals(6, localConsumer.map.get("foo").intValue());
        assertTrue(globalConsumer.map.isEmpty());

        localConsumer.latchRef.set(new CountDownLatch(1));
        new ContainerThread(new AddTask(metric, "foo", 9), localConsumer).start();
        localConsumer.latchRef.get().await(600, TimeUnit.SECONDS);
        assertEquals(15, localConsumer.map.get("foo").intValue());
        assertTrue(globalConsumer.map.isEmpty());
    }

    private static class SetTask implements Runnable {

        final Metric metric;
        final String key;
        final Number val;

        public SetTask(Metric metric, String key, Number val) {
            this.metric = metric;
            this.key = key;
            this.val = val;
        }

        @Override
        public void run() {
            metric.set(key, val, null);
        }
    }

    private static class AddTask implements Runnable {

        final Metric metric;
        final String key;
        final Number val;

        public AddTask(Metric metric, String key, Number val) {
            this.metric = metric;
            this.key = key;
            this.val = val;
        }

        @Override
        public void run() {
            metric.add(key, val, null);
        }
    }

    private static class MyConsumer implements MetricConsumer {

        final ConcurrentMap<String, Integer> map = new ConcurrentHashMap<>();
        final AtomicReference<CountDownLatch> latchRef = new AtomicReference<>();
        final Metric.Context ctx = new Metric.Context() { };

        @Override
        public void set(String key, Number val, Metric.Context ctx) {
            map.put(key, val.intValue());
            CountDownLatch latch = latchRef.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void add(String key, Number val, Metric.Context ctx) {
            map.put(key, map.get(key) + val.intValue());
            CountDownLatch latch = this.latchRef.get();
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public Metric.Context createContext(Map<String, ?> properties) {
            return ctx;
        }
    }
}
