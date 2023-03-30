// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring.builder;

import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A mutable helper class representing the user defined metrics and consumers.
 *
 * @author gjoranv
 */
public class Metrics {

    private final Map<String, MetricsConsumer> consumers = new LinkedHashMap<>();

    public void addConsumer(MetricsConsumer consumer) {
        consumers.put(consumer.id(), consumer);
    }

    public Map<String, MetricsConsumer> getConsumers() {
        return Collections.unmodifiableMap(consumers);
    }

    public boolean hasConsumerIgnoreCase(String id) {
        return consumers.keySet().stream()
                .anyMatch(existing -> existing.equalsIgnoreCase(id));
    }

}
