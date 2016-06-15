// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.jdisc.metrics.jmx;

import com.yahoo.jdisc.metrics.jmx.core.MetricUnit;

import java.util.*;
import java.util.logging.Logger;

/**
 * <p>A multi-source class which can be periodically scheduled to poll the data from the sources</p>
 *
 * <p>Note: It is possible that multiple {@link ConsumerContextMetric}s are simultaneously added as data sources in a
 * multi-threaded environment. However, the corresponding setter in {@link ComponentMetricMBean} is synchronized.</p>
 *
 * @author <a href="mailto:alain@yahoo-inc.com">Alain Wan Buen Cheong</a>
 */
class MultiSourceComponentMetric {

    private final static Logger logger = Logger.getLogger(MultiSourceComponentMetric.class.getName());

    // Accessors / setters are synchronized
    private final List<ConsumerContextMetric> consumerContextMetrics = new LinkedList<ConsumerContextMetric>();

    // We need to preserve some values e.g. Counters after each snapshot.
    private final Map<String, MetricUnit> persistentMetrics = new HashMap<String, MetricUnit>();

    public MultiSourceComponentMetric(ConsumerContextMetric contextMetric) {
        addConsumerContextMetric(contextMetric);
    }

    /**
     * Add a data source
     *
     * @return true if added successfully, false otherwise
     */
    public boolean addConsumerContextMetric(ConsumerContextMetric contextMetric) {
        return consumerContextMetrics.add(contextMetric);

    }

    /**
     * Number of data sources
     *
     * @return data source count
     */
    public int getSourceCount() {
        return consumerContextMetrics.size();
    }

    /**
     * Merge the data sources if the data type is persistent
     */
    public Map<String, MetricUnit> snapshot() {
        Map<String, MetricUnit> snapshot = new HashMap<String, MetricUnit>();
        for (ConsumerContextMetric contextMetric : consumerContextMetrics) {
            Map<String, MetricUnit> contextMetricSnapshot = contextMetric.snapshot();
            for (Map.Entry<String, MetricUnit> entry : contextMetricSnapshot.entrySet()) {
                String key = entry.getKey();
                MetricUnit value = entry.getValue();
                if (value.isPersistent()) {
                    MetricUnit prev = persistentMetrics.get(key);
                    if (prev == null) {
                        persistentMetrics.put(key, value);
                    } else {
                        value = addMetric(prev, value);
                    }
                } else {
                    MetricUnit snapshotValue = snapshot.get(key);
                    if (snapshotValue != null) {
                        value = addMetric(snapshotValue, value);
                    }
                }
                snapshot.put(key, value);
            }
        }
        return snapshot;
    }

    private MetricUnit addMetric(MetricUnit to, MetricUnit from) {
        try {
            to.addMetric(from);
        } catch (RuntimeException e) {
            logger.warning("Can not merge context metric: " + e.getLocalizedMessage() + ".");
        }
        return to;
    }

}
