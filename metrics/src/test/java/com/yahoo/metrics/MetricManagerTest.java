// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.metrics;

import org.junit.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;

/**
 * @author thomasg
 */
public class MetricManagerTest {

    @Test
    public void requireThatManagerCanBeStopped() throws InterruptedException {
        final MetricManager manager = new MetricManager(new DummyTimer());
        Thread managerThread = new Thread(manager);
        managerThread.start();
        Thread stopThread = new Thread(new Runnable() {

            @Override
            public void run() {
                manager.stop();
            }
        });
        stopThread.start();
        stopThread.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(stopThread.isAlive());
        managerThread.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(managerThread.isAlive());
    }

    @Test
    public void requireThatManagerCanBeStoppedBeforeStarting() throws InterruptedException {
        final MetricManager manager = new MetricManager(new DummyTimer());
        Thread thread = new Thread(new Runnable() {

            @Override
            public void run() {
                manager.stop();
            }
        });
        thread.start();
        thread.join(TimeUnit.SECONDS.toMillis(60));
        assertFalse(thread.isAlive());
    }

    @Test
    public void requireThatManagerCanNotBeStartedAfterItHasBeenStopped() {
        final MetricManager manager = new MetricManager(new DummyTimer());
        manager.stop();
        try {
            manager.run();
            fail();
        } catch (IllegalStateException e) {

        }
    }

    @Test
    public void testSimpleLogging() {
        DummyTimer timer = new DummyTimer();
        MetricManager manager = new MetricManager(timer);
        SimpleMetricSet sms = new SimpleMetricSet("foo", "", "", null);
        manager.registerMetric(sms);

        CountMetric bar = new CountMetric("bar", "", "", sms);
        bar.inc(3);

        manager.addMetricToConsumer("log", "foo.bar");

        DummyEventLogger logger = new DummyEventLogger();
        manager.tick(logger);
        //assertEquals("CNT foo_bar: 0\n", logger.output);

        timer.ms = 300000;

        manager.tick(logger);
        assertEquals("CNT foo_bar: 0\nCNT foo_bar: 3\n", logger.output.toString());
    }

    @Test
    public void testSumLogging() {
        DummyTimer timer = new DummyTimer();
        MetricManager manager = new MetricManager(timer);
        SimpleMetricSet sms = new SimpleMetricSet("foo", "", "", null);
        manager.registerMetric(sms);

        CountMetric v1 = new CountMetric("v1", "", "", sms);
        CountMetric v2 = new CountMetric("v2", "", "", sms);
        CountMetric v3 = new CountMetric("v3", "", "", sms);
        SumMetric sum = new SumMetric("sum", "", "", sms);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);
        sum.addMetricToSum(v3);

        v1.inc(3);
        v2.inc(1);
        v3.inc(2);

        manager.addMetricToConsumer("log", "foo.sum");

        DummyEventLogger logger = new DummyEventLogger();
        manager.tick(logger);
        assertEquals("CNT foo_sum: 0\n", logger.output.toString());

        timer.ms = 300000;

        manager.tick(logger);
        assertEquals("CNT foo_sum: 0\nCNT foo_sum: 6\n", logger.output.toString());
    }

    @Test
    public void testSumAndSetLogging() {
        DummyTimer timer = new DummyTimer();
        timer.ms = 1000000;
        MetricManager manager = new MetricManager(timer);
        SimpleMetricSet sms = new SimpleMetricSet("foo", "", "", null);
        manager.registerMetric(sms);

        SimpleMetricSet v1 = new SimpleMetricSet("v1", "", "", sms);
        SimpleMetricSet v2 = new SimpleMetricSet("v2", "", "", sms);

        CountMetric v1_a = new CountMetric("a", "", "", v1);
        SummedLongValueMetric v1_b = new SummedLongValueMetric("b", "", "", v1);
        AveragedLongValueMetric v1_c = new AveragedLongValueMetric("c", "", "", v1);

        CountMetric v2_a = new CountMetric("a", "", "", v2);
        SummedLongValueMetric v2_b = new SummedLongValueMetric("b", "", "", v2);
        AveragedLongValueMetric v2_c = new AveragedLongValueMetric("c", "", "", v2);

        SumMetric sum = new SumMetric("sum", "", "", sms);

        sum.addMetricToSum(v1);
        sum.addMetricToSum(v2);

        v1_a.inc(3);
        v1_b.addValue((long)1);
        v1_c.addValue((long)4);
        v2_a.inc(2);
        v2_b.addValue((long)2);
        v2_c.addValue((long)8);

        manager.addMetricToConsumer("log", "foo.sum.a");
        manager.addMetricToConsumer("log", "foo.sum.b");
        manager.addMetricToConsumer("log", "foo.sum.c");

        {
            DummyEventLogger logger = new DummyEventLogger();
            assertEquals(300000, manager.tick(logger));
            assertEquals("CNT foo_sum_a: 0\n", logger.output.toString());
        }

        timer.ms = 1300000;

        {
            DummyEventLogger logger = new DummyEventLogger();
            assertEquals(300000, manager.tick(logger));
            assertEquals("CNT foo_sum_a: 5\nVAL foo_sum_b: 3.0\nVAL foo_sum_c: 6.0\n", logger.output.toString());
        }

        v1_a.inc(4);
        v1_b.addValue((long)2);
        v1_b.addValue((long)6);
        v2_b.addValue((long)4);

        timer.ms = 1600000;

        {
            DummyEventLogger logger = new DummyEventLogger();
            assertEquals(300000, manager.tick(logger));
            assertEquals("CNT foo_sum_a: 9\nVAL foo_sum_b: 10.0\n", logger.output.toString());
        }
    }

    private static class DummyEventLogger implements EventLogger {

        StringBuilder output = new StringBuilder();

        @Override
        public void value(String name, double value) {
            output.append("VAL " + name + ": " + value + "\n");
        }

        @Override
        public void count(String name, long value) {
            output.append("CNT " + name + ": " + value + "\n");
        }
    }
}
