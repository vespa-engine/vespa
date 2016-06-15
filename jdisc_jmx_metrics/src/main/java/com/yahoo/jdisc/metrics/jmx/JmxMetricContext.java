// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.Metric;
import com.yahoo.jdisc.metrics.jmx.cloud.JmxMetricConfig;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import java.util.Hashtable;
import java.util.Map;

/**
 * <p>An instance of this class should be created by calling {@link JmxMetricConsumer#createContext(java.util.Map)}</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
public class JmxMetricContext implements Metric.Context {

    private final ObjectName objectName;

    public JmxMetricContext(JmxMetricConfig metricConfig, Map<String, ?> dimensions) {
        metricConfig.getClass(); // throws NullPointerException
        dimensions.getClass();

        Hashtable<String, String> dimensionsTable = new Hashtable<String, String>();
        for (Map.Entry<String, ?> entry : dimensions.entrySet()) {
            dimensionsTable.put(entry.getKey(), String.valueOf(entry.getValue()));
        }
        if (dimensionsTable.isEmpty()) {
            dimensionsTable.put("name", "JDisc");
        }
        try {
            objectName = new ObjectName(metricConfig.objectNameDomain(), dimensionsTable);
        } catch (MalformedObjectNameException e) {
            throw new IllegalArgumentException("Could not create ObjectName.", e);
        }
    }

    public ObjectName getObjectName() {
        return objectName;
    }

    @Override
    public boolean equals(Object obj) {
        if(obj == null || ! (obj instanceof JmxMetricContext)) {
            return false;
        }
        return objectName.equals(((JmxMetricContext)obj).objectName);
    }

    @Override
    public int hashCode() {
        return objectName.hashCode();
    }

}
