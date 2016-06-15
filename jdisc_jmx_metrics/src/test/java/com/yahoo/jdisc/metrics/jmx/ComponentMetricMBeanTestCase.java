// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.google.inject.Guice;
import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;
import org.testng.annotations.Test;

import javax.management.AttributeList;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanFeatureInfo;
import javax.management.MBeanInfo;
import javax.management.ReflectionException;


import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.testng.AssertJUnit.assertEquals;

/**
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class ComponentMetricMBeanTestCase {

    private final Timer timer = Guice.createInjector().getInstance(Timer.class);

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatConstructorThrowsException() {
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(null, null, timer);
        componentMetricMBean.toString();
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatGetAttributeThrowsException() throws Exception {
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(new JmxMetricConfig(new JmxMetricConfig.Builder()),
                                                    new ConsumerContextMetric(10000), timer);
        componentMetricMBean.getAttribute(null);
    }

    @Test(expectedExceptions = NullPointerException.class)
    public void requireThatGetAttributesThrowsException() throws Exception {
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(new JmxMetricConfig(new JmxMetricConfig.Builder()),
                                                    new ConsumerContextMetric(10000), timer);
        componentMetricMBean.getAttributes(null);
    }

    @Test(expectedExceptions = ReflectionException.class)
    public void requireThatInvokeThrowsException() throws Exception {
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(new JmxMetricConfig(new JmxMetricConfig.Builder()),
                new ConsumerContextMetric(Integer.MAX_VALUE), timer);
        componentMetricMBean.invoke("blah", null, null);
    }

    @Test
    public void requireThatGetAttributesSupportsNonFoundValues() {
        ConsumerContextMetric componentMetric = new ConsumerContextMetric(10000);
        componentMetric.setMetric("key1", 10);
        componentMetric.setMetric("key2", 15);
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.minSnapshotIntervalMillis(0);
        JmxMetricConfig config = new JmxMetricConfig(builder);
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(config, componentMetric, timer);
        AttributeList list = componentMetricMBean.getAttributes(new String[]{ "key1", "key2", "key3" });
        assertEquals(2, list.size());
    }

    @Test
    public void requireThatGetMBeanInfoWorks() throws Exception {
        ConsumerContextMetric componentMetric = new ConsumerContextMetric(10000);
        componentMetric.setMetric("key1", 10);
        componentMetric.setMetric("key2", 15);
        JmxMetricConfig.Builder builder = new JmxMetricConfig.Builder();
        builder.minSnapshotIntervalMillis(0);
        JmxMetricConfig config = new JmxMetricConfig(builder);
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(config, componentMetric, timer);
        //componentMetricMBean.poll();     // TODO: Add if we have a timer
        MBeanInfo info = componentMetricMBean.getMBeanInfo();
        MBeanAttributeInfo[] attributes = info.getAttributes();

        List<String> keys = Arrays.stream(attributes).map(MBeanFeatureInfo::getName).collect(Collectors.toList());
        assertEquals(keys, Arrays.asList("key1", "key2"));
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatSetAttributeIsNotImplemented() throws Exception {
        ConsumerContextMetric componentMetric = new ConsumerContextMetric(10000);
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(new JmxMetricConfig(new JmxMetricConfig.Builder()),
                componentMetric, timer);
        componentMetricMBean.setAttribute(null);
    }

    @Test(expectedExceptions = RuntimeException.class)
    public void requireThatSetAttributesIsNotImplemented() throws Exception {
        ConsumerContextMetric componentMetric = new ConsumerContextMetric(10000);
        ComponentMetricMBean componentMetricMBean = new ComponentMetricMBean(new JmxMetricConfig(new JmxMetricConfig.Builder()),
                componentMetric, timer);
        componentMetricMBean.setAttributes(null);
    }

}
