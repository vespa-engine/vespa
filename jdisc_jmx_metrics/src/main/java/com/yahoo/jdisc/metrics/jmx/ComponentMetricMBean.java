// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.Timer;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;
import com.yahoo.jdisc.metrics.jmx.core.AbstractDynamicMBean;
import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;

import javax.management.*;
import java.util.*;

/**
 * <p>This class provides a read-only implementation of a {@link DynamicMBean} that exposes JDisc metrics.
 * It is backed internally by a {@link MultiSourceComponentMetric}</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public final class ComponentMetricMBean extends AbstractDynamicMBean implements ConsumerContextMetricReader {

    private final String description;
    private final MultiSourceComponentMetric componentMetric;
    private Map<String, MetricUnit> snapshot = Collections.emptyMap();
    private long nextSnapshotTime;
    private final long snapshotInterval;
    private final Timer timer;

    public ComponentMetricMBean(JmxMetricConfig metricConfig, ConsumerContextMetric contextMetric, Timer timer) {
        metricConfig.getClass(); // throws NullPointerException
        contextMetric.getClass();

        this.componentMetric = new MultiSourceComponentMetric(contextMetric);
        description = metricConfig.beanDescription();
        snapshotInterval = metricConfig.minSnapshotIntervalMillis();
        this.timer = timer;
        nextSnapshotTime = timer.currentTimeMillis() + snapshotInterval;
    }

    @Override
    public synchronized Object getAttribute(String attribute) throws AttributeNotFoundException, MBeanException, ReflectionException {
        attribute.getClass(); // throws NullPointerException
        MetricUnit metricUnit = snapshot().get(attribute);
        if (metricUnit == null) {
            // If attribute name not recognized
            throw new AttributeNotFoundException("Can not find " + attribute + " attribute in " + getClass().getName() + ".");
        }
        return metricUnit.getValue();
    }

    @Override
    public synchronized AttributeList getAttributes(String[] attributes) {
        attributes.getClass(); // throws NullPointerException
        Map<String, MetricUnit> snapshot = snapshot();
        AttributeList list = new AttributeList();
        for (String name : attributes) {
            MetricUnit metricUnit = snapshot.get(name);
            if (metricUnit != null) {
                list.add(new Attribute(name, metricUnit.getValue()));
            }
        }
        return list;
    }

    @Override
    public synchronized MBeanInfo getMBeanInfo() {

        Set<String> jDiscStatsKeys = snapshot().keySet();
        MBeanAttributeInfo[] attributes = new MBeanAttributeInfo[jDiscStatsKeys.size()];
        Iterator<String> itr = jDiscStatsKeys.iterator();
        for (int i=0; i < attributes.length; i++ ) {
            String name = itr.next();
            attributes[i] = new MBeanAttributeInfo(
                name,  // name
                "java.lang.Number",        // type
                "JDisc stat for: " + name, // description
                true,  // readable
                false, // writable
                false  // isIs
            );
        }
        return new MBeanInfo(getClass().getName(),
                             description,
                             attributes,
                             null,  // just default constructor
                             null,  // no operations
                             null); // no notifications
    }

    @Override
    public Object invoke(String actionName, Object[] params, String[] signature) throws MBeanException, ReflectionException {
        if (actionName.equals("addDataSource") && params.length == 1) {
            return addDataSource((ConsumerContextMetric)params[0]);
        }
        if (actionName.equals("dataSourceCount")) {
            return dataSourceCount();
        }
        if (actionName.equals("snapshot")) {
            return snapshot();
        }
        throw new ReflectionException(new NoSuchMethodException(actionName));
    }

    @Override
    public synchronized boolean addDataSource(ConsumerContextMetric contextMetric) {
        return componentMetric.addConsumerContextMetric(contextMetric);
    }

    // Used for testing
    @Override
    public synchronized int dataSourceCount() {
        return componentMetric.getSourceCount();
    }

    @Override
    public synchronized Map<String, MetricUnit> snapshot() {
        long currentTime = timer.currentTimeMillis();
        if (currentTime >= nextSnapshotTime) {
            snapshot = componentMetric.snapshot();
            nextSnapshotTime = currentTime + snapshotInterval;
        }
        return snapshot;
    }

}
