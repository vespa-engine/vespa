// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.google.inject.Guice;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.application.MetricConsumer;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;
import org.testng.annotations.Test;

import javax.management.*;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Hashtable;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class IntegrationTestCase {

    Timer timer = Guice.createInjector().getInstance(Timer.class);

    @Test
    public void requireThatACounterCanBeCapturedWithNullContext() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("com.jdisc.jmx:name=JDisc");
        try {
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            MetricConsumer consumer = provider.get();
            consumer.add("key1", 100, null);
            assertEquals(Long.valueOf(100), mbs.getAttribute(objectName, "key1"));
            // Make sure to call this every time
        } finally {
            mbs.unregisterMBean(objectName);
        }
    }

    @Test
    public void requireThatGaugesCanBeCapturedWithNullContext() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("com.jdisc.jmx:name=JDisc");
        try {
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            MetricConsumer consumer = provider.get();
            consumer.set("key1", 100, null);
            consumer.set("key1", 50, null);
            consumer.set("key2", 50, null);
            AttributeList list = mbs.getAttributes(objectName, new String[]{"key1", "key2"});
            assertEquals(75.0, ((Attribute) list.get(0)).getValue());
            assertEquals(50.0, ((Attribute)list.get(1)).getValue());

        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    @Test(expectedExceptions = AttributeNotFoundException.class)
    public void requireThatANonExistentAttributeThrowsException() throws Exception {
        JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
        MetricConsumer consumer = provider.get();
        consumer.add("key1", 100, null);
        ObjectName objectName = new ObjectName("com.jdisc.jmx:name=JDisc");
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            assertEquals(100.0, mbs.getAttribute(objectName, "key2"));
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    @Test
    public void requireThatACounterCanBeCapturedWithEmptyContext() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("com.jdisc.jmx:name=JDisc");
        try {
            JmxMetricContext context = new JmxMetricContext(config(), new HashMap<String, Object>());
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            MetricConsumer consumer = provider.get();
            consumer.add("key1", 100, context);
            assertEquals(Long.valueOf(100), mbs.getAttribute(objectName, "key1"));
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    @Test
    public void requireThatACounterCanBeCapturedWithOneDimension() throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        Hashtable<String, String> contextMap = new Hashtable<String, String>();
        contextMap.put("dimension1", "8080");
        ObjectName objectName = new ObjectName("com.jdisc.jmx", contextMap);
        try {
            JmxMetricContext context = new JmxMetricContext(config(), contextMap);
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            MetricConsumer consumer = provider.get();
            consumer.add("key2", 101, context);
            assertEquals(Long.valueOf(101), mbs.getAttribute(objectName, "key2"));
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    @Test
    public void requireThatCountersCanBeCapturedWithMultipleDimensionsOneConsumerOneMBean() throws Exception {
        JmxMetricContext context = new JmxMetricContext(config(), context());

        JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
        MetricConsumer consumer = provider.get();
        consumer.add("bytesSent", 101, context);
        consumer.add("bytesReceived", 97, context);
        consumer.add("timeout", 1, context);
        consumer.add("timeout", 2, context);
        consumer.add("bytesSent", 1, context);
        consumer.add("bytesSent", 10, context);
        consumer.add("bytesReceived", 99, context);
        consumer.add("bytesReceived", 98, context);
        ObjectName objectName = new ObjectName("com.jdisc.jmx", context());
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        try {
            //doFlush(objectName);
            AttributeList results = mbs.getAttributes(objectName, new String[]{"timeout", "bytesSent", "bytesReceived"});

            assertEquals(3, results.size());

            Attribute attribute1 = (Attribute) results.get(0);
            Attribute attribute2 = (Attribute) results.get(1);
            Attribute attribute3 = (Attribute) results.get(2);

            assertEquals("timeout", attribute1.getName());
            assertEquals(Long.valueOf(3), attribute1.getValue());

            assertEquals("bytesSent", attribute2.getName());
            assertEquals(Long.valueOf(112), attribute2.getValue());

            assertEquals("bytesReceived", attribute3.getName());
            assertEquals(Long.valueOf(294), attribute3.getValue());
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    @Test
    public void requireThatCountersAndGaugesCanBeCapturedWithMultipleDimensionsOneConsumerOneMBean() throws Exception {
        JmxMetricContext context = new JmxMetricContext(config(), context());

        JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("com.jdisc.jmx", context());
        try {
            MetricConsumer consumer = provider.get();
            consumer.add("bytesSent", 101, context);
            consumer.add("bytesReceived", 97, context);
            consumer.add("timeout", 1, context);
            consumer.set("responseTime", 100.0, context);  // gauge
            consumer.add("timeout", 2, context);
            consumer.add("bytesSent", 1, context);
            consumer.add("bytesSent", 10, context);
            consumer.add("bytesReceived", 99, context);
            consumer.add("bytesReceived", 98, context);
            consumer.set("processTime", 100.0, context);  // gauge
            consumer.set("responseTime", 200.0, context);  // gauge
            consumer.set("responseTime", 300.0, context);  // gauge
            AttributeList results = mbs.getAttributes(objectName, new String[]{"timeout", "bytesSent", "bytesReceived",
                    "processTime", "responseTime"});
            assertEquals(5, results.size());

            Attribute attribute1 = (Attribute) results.get(0);
            Attribute attribute2 = (Attribute) results.get(1);
            Attribute attribute3 = (Attribute) results.get(2);
            Attribute attribute4 = (Attribute) results.get(3);
            Attribute attribute5 = (Attribute) results.get(4);

            assertEquals("timeout", attribute1.getName());
            assertEquals(Long.valueOf(3), attribute1.getValue());

            assertEquals("bytesSent", attribute2.getName());
            assertEquals(Long.valueOf(112), attribute2.getValue());

            assertEquals("bytesReceived", attribute3.getName());
            assertEquals(Long.valueOf(294), attribute3.getValue());

            assertEquals("processTime", attribute4.getName());
            assertEquals(100.0, attribute4.getValue());

            assertEquals("responseTime", attribute5.getName());
            assertEquals(200.0, attribute5.getValue());

            assertEquals(1, dataSourceCount(objectName));
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
        }
    }

    private static int TRY_COUNT=10;

    @Test
    public void requireThatCountersAndGaugesCanBeCapturedInAThreadedEnv() throws Exception {

        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = new ObjectName("com.jdisc.jmx", context());
        ObjectName objectName2 = new ObjectName("com.jdisc.jmx", context2());
        try {
            RunnableConsumerBase rcb1 = new RunnableConsumer1();
            RunnableConsumerBase rcb2 = new RunnableConsumer2();
            RunnableConsumerBase rcb3 = new RunnableConsumer3();

            RunnableConsumerBase2 rcb1_2 = new RunnableConsumer1_2();
            RunnableConsumerBase2 rcb2_2 = new RunnableConsumer2_2();
            RunnableConsumerBase2 rcb3_2 = new RunnableConsumer3_2();

            for (int i=1;i<=TRY_COUNT; i++) {
                Thread t1 = new Thread(rcb1);
                Thread t2 = new Thread(rcb2);
                Thread t3 = new Thread(rcb3);
                Thread t1_2 = new Thread(rcb1_2);
                Thread t2_2 = new Thread(rcb2_2);
                Thread t3_2 = new Thread(rcb3_2);

                t1.start();t2.start();t3.start();
                t1_2.start();t2_2.start();t3_2.start();
                t1.join();t2.join();t3.join();
                t1_2.join();t2_2.join();t3_2.join();

                verifyOnce(objectName, i);
                verifyOnce(objectName2, i);
            }
        } finally {
            // Make sure to call this every time
            mbs.unregisterMBean(objectName);
            mbs.unregisterMBean(objectName2);
        }
    }

    private void verifyOnce(ObjectName objectName, int idx) throws Exception {
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        AttributeList results = mbs.getAttributes(objectName, new String[]{"timeout", "bytesSent", "bytesReceived",
                "processTime", "responseTime", "responseTime2", "bytesBlocked", "cycleTime"});
        assertEquals(8, results.size());

        Attribute attribute1 = (Attribute) results.get(0);
        Attribute attribute2 = (Attribute) results.get(1);
        Attribute attribute3 = (Attribute) results.get(2);
        Attribute attribute4 = (Attribute) results.get(3);
        Attribute attribute5 = (Attribute) results.get(4);
        Attribute attribute6 = (Attribute) results.get(5);
        Attribute attribute7 = (Attribute) results.get(6);
        Attribute attribute8 = (Attribute) results.get(7);

        assertEquals("timeout", attribute1.getName());
        assertEquals(Long.valueOf(6*idx), attribute1.getValue());

        assertEquals("bytesSent", attribute2.getName());
        assertEquals(Long.valueOf(223*idx), attribute2.getValue());

        assertEquals("bytesReceived", attribute3.getName());
        assertEquals(Long.valueOf(587*idx), attribute3.getValue());

        assertEquals("processTime", attribute4.getName());
        assertEquals(75.0, attribute4.getValue());

        assertEquals("responseTime", attribute5.getName());
        assertEquals(180.0, attribute5.getValue());

        assertEquals("responseTime2", attribute6.getName());
        assertEquals(300.0, attribute6.getValue());

        assertEquals("bytesBlocked", attribute7.getName());
        assertEquals(Long.valueOf(33*idx), attribute7.getValue());

        assertEquals("cycleTime", attribute8.getName());
        assertEquals(10.0, attribute8.getValue());

        assertEquals(3, dataSourceCount(objectName));
    }


    abstract class RunnableConsumerBase implements Runnable {

        protected MetricConsumer consumer;
        protected JmxMetricContext context = new JmxMetricContext(config(), context());

        public RunnableConsumerBase() {
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            consumer = provider.get();
        }
    }

    abstract class RunnableConsumerBase2 implements Runnable {

        protected MetricConsumer consumer;
        protected JmxMetricContext context = new JmxMetricContext(config(), context2());

        public RunnableConsumerBase2() {
            JmxMetricConsumer.Provider provider = new JmxMetricConsumer.Provider(config(), timer);
            consumer = provider.get();
        }
    }

    class RunnableConsumer1 extends RunnableConsumerBase {

        public void run() {
            try {
                consumer.add("bytesSent", 101, context);
                consumer.add("bytesReceived", 97, context);
                consumer.add("timeout", 1, context);
                consumer.set("responseTime", 100.0, context);  // gauge
                consumer.add("timeout", 2, context);
                consumer.add("bytesSent", 1, context);
                consumer.add("bytesSent", 10, context);
                consumer.add("bytesReceived", 99, context);
                consumer.add("bytesReceived", 98, context);
                consumer.set("processTime", 100.0, context);  // gauge
                consumer.set("responseTime", 200.0, context);  // gauge
                consumer.set("responseTime", 300.0, context);  // gauge
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    class RunnableConsumer2 extends RunnableConsumerBase {

        public void run() {
            try {
                consumer.add("bytesSent", 101, context);
                consumer.add("bytesReceived", 97, context);
                consumer.add("timeout", 1, context);
                consumer.set("responseTime", 100.0, context);  // gauge
                consumer.add("timeout", 2, context);
                consumer.add("bytesSent", 1, context);
                consumer.add("bytesSent", 9, context);
                consumer.add("bytesReceived", 99, context);
                consumer.add("bytesReceived", 97, context);
                consumer.set("processTime", 50.0, context);  // gauge
                consumer.set("responseTime", 200.0, context);  // gauge
                consumer.set("responseTime2", 300.0, context);  // gauge
                consumer.add("bytesBlocked", 13.0, context);
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    class RunnableConsumer3 extends RunnableConsumerBase {

        public void run() {
            try {
                consumer.add("bytesBlocked", 11.0, context);
                consumer.add("bytesBlocked", 9.0, context);
                consumer.set("cycleTime", 10.0, context); // gauge
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    class RunnableConsumer1_2 extends RunnableConsumerBase2 {

        public void run() {
            try {
                consumer.add("bytesSent", 101, context);
                consumer.add("bytesReceived", 97, context);
                consumer.add("timeout", 1, context);
                consumer.set("responseTime", 100.0, context);  // gauge
                consumer.add("timeout", 2, context);
                consumer.add("bytesSent", 1, context);
                consumer.add("bytesSent", 10, context);
                consumer.add("bytesReceived", 99, context);
                consumer.add("bytesReceived", 98, context);
                consumer.set("processTime", 100.0, context);  // gauge
                consumer.set("responseTime", 200.0, context);  // gauge
                consumer.set("responseTime", 300.0, context);  // gauge
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    class RunnableConsumer2_2 extends RunnableConsumerBase2 {

        public void run() {
            try {
                consumer.add("bytesSent", 101, context);
                consumer.add("bytesReceived", 97, context);
                consumer.add("timeout", 1, context);
                consumer.set("responseTime", 100.0, context);  // gauge
                consumer.add("timeout", 2, context);
                consumer.add("bytesSent", 1, context);
                consumer.add("bytesSent", 9, context);
                consumer.add("bytesReceived", 99, context);
                consumer.add("bytesReceived", 97, context);
                consumer.set("processTime", 50.0, context);  // gauge
                consumer.set("responseTime", 200.0, context);  // gauge
                consumer.set("responseTime2", 300.0, context);  // gauge
                consumer.add("bytesBlocked", 13.0, context);
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    class RunnableConsumer3_2 extends RunnableConsumerBase2 {

        public void run() {
            try {
                consumer.add("bytesBlocked", 11.0, context);
                consumer.add("bytesBlocked", 9.0, context);
                consumer.set("cycleTime", 10.0, context); // gauge
            } catch (Exception e) {
                System.out.println(e.getLocalizedMessage());
                assertFalse(true);
            }
        }
    }

    protected Hashtable<String, String> context() {
        Hashtable<String, String> contextMap = new Hashtable<String, String>();
        contextMap.put("port", "8080");
        contextMap.put("server", "http");
        contextMap.put("bundle", "http_service");
        return contextMap;
    }

    protected Hashtable<String, String> context2() {
        Hashtable<String, String> contextMap = new Hashtable<String, String>();
        contextMap.put("port", "8081");
        contextMap.put("server", "https");
        contextMap.put("bundle", "http_service");
        return contextMap;
    }

    protected int dataSourceCount(ObjectName objectName) {
        ConsumerContextMetricReader componentMetricMBean = JMX.newMBeanProxy(ManagementFactory.getPlatformMBeanServer(),
                objectName,
                ConsumerContextMetricReader.class);
        return componentMetricMBean.dataSourceCount();
    }

    protected JmxMetricConfig config() {
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain("com.jdisc.jmx");
        builder.minSnapshotIntervalMillis(0);
        JmxMetricConfig config = new JmxMetricConfig(builder);
        return config;
    }
}
