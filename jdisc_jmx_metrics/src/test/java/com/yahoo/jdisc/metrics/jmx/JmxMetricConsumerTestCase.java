// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.google.inject.Guice;
import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;
import org.testng.annotations.Test;

import javax.management.JMException;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static org.testng.AssertJUnit.assertEquals;
import static org.testng.AssertJUnit.assertFalse;
import static org.testng.AssertJUnit.assertTrue;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class JmxMetricConsumerTestCase {

    Timer timer = Guice.createInjector().getInstance(Timer.class);

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatConstructorCanThrowException() {
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain(":::");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        JmxMetricConsumer consumer = new JmxMetricConsumer(config, timer);
        consumer.toString();
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatCreateContextCanThrowException() {
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain(":::");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        JmxMetricConsumer consumer = new JmxMetricConsumer(config, timer);
        consumer.createContext(null);
        consumer.toString();
    }

    ConsumerContextMetric dataSource = new ConsumerContextMetric(Integer.MAX_VALUE);

    @Test
    public void requireThatNewMBeanCanBeRegisteredWithNoDimensions() {

        JmxMetricConfig config = new JmxMetricConfig(new JmxMetricConfig.Builder());

        try {
            new JmxMetricConsumer(config, timer).createAndRegisterMBean(createContext(), dataSource, config, timer);
        } catch (Exception e) {
            assertTrue(false);
        }
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = null;
        try {
            objectName = new ObjectName("test.jdisc.metrics.jmx:name=JDisc");
        } catch (Exception e) {
        } finally {
            assertTrue(mbs.isRegistered(objectName));
            try {
                mbs.unregisterMBean(objectName);
            } catch (Exception e) {
                assertFalse(true);
            }
        }
    }

    @Test
    public void requireThatNewMBeanCanBeRegisteredWithEmptyContext() {

        JmxMetricConfig config = new JmxMetricConfig(new JmxMetricConfig.Builder());

        try {
            new JmxMetricConsumer(config, timer).createAndRegisterMBean(createContext(), dataSource, config, timer);
        } catch (Exception e) {
            assertTrue(false);
        }
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = null;
        try {
            objectName = new ObjectName("test.jdisc.metrics.jmx:name=JDisc");
        } catch (Exception e) {
        } finally {
            assertTrue(mbs.isRegistered(objectName));
            try {
                mbs.unregisterMBean(objectName);
            } catch (Exception e) {
                assertFalse(true);
            }
        }
    }

    @Test
    public void requireThatNewMBeanCanBeRegisteredWithDimensions() {
        Map<String, Object> contextMap = new HashMap<String, Object>();
        contextMap.put("key1", "value1");
        contextMap.put("key2", "value2");
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain("test.jdisc.metrics.jmx");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        JmxMetricContext context = new JmxMetricContext(config, contextMap);

        try {
            new JmxMetricConsumer(config, timer).createAndRegisterMBean(context, dataSource, config, timer);
        } catch (Exception e) {
            assertTrue(false);
        }
        Hashtable<String, String> expectedDimensions = new Hashtable<String, String>();
        expectedDimensions.put("key1", "value1");
        expectedDimensions.put("key2", "value2");
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = null;
        try {
            objectName = new ObjectName("test.jdisc.metrics.jmx", expectedDimensions);
        } catch (Exception e) {
            assertFalse(true);
        } finally {
            assertTrue(mbs.isRegistered(objectName));
            try {
                mbs.unregisterMBean(objectName);
            } catch (Exception e) {
                assertFalse(true);
            }
        }
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatExceptionCanBeThrown() throws JMException {
        JmxMetricConfig config = new JmxMetricConfig(new JmxMetricConfig.Builder());

        new JmxMetricConsumer(config, timer).createAndRegisterMBean(new DummyContext(), dataSource, config, timer);
        assertTrue(false);
    }

    @Test
    public void requireThatDataSourceCanBeRegisteredWithExistingMBean() {

        // Register MBean first
        JmxMetricConfig config = new JmxMetricConfig(new JmxMetricConfig.Builder());

        try {
            new JmxMetricConsumer(config, timer).createAndRegisterMBean(createContext(), dataSource, config, timer);
        } catch (Exception e) {
            assertTrue(false);
        }
        MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
        ObjectName objectName = null;
        try {
            objectName = new ObjectName("test.jdisc.metrics.jmx:name=JDisc");
        } catch (Exception e) {
        } finally {
            assertTrue(mbs.isRegistered(objectName));
        }

        ConsumerContextMetric dataSource2 = new ConsumerContextMetric(Integer.MAX_VALUE);
        // Register same MBean, different data source
        try {
            new JmxMetricConsumer(config, timer).createAndRegisterMBean(createContext(), dataSource2, config, timer);
        } catch (Exception e) {
            assertTrue(false);
        }
        ConsumerContextMetricReader componentMetricMBean = JMX.newMBeanProxy(mbs,
                objectName,
                ConsumerContextMetricReader.class);
        assertEquals(2, componentMetricMBean.dataSourceCount());

    }

    private JmxMetricContext createContext() {
        Map<String, String> dimensions = new HashMap<String, String>();
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.objectNameDomain("test.jdisc.metrics.jmx");
        JmxMetricConfig config = new JmxMetricConfig(builder);
        return new JmxMetricContext(config, dimensions);
    }

    class DummyContext implements Metric.Context {

    }

}
