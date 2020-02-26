// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.autoscale;

import com.yahoo.config.provision.ApplicationId;

import java.util.Collection;

/**
 * Interface to retrieve metrics on (tenant) nodes.
 *
 * @author bratseth
 */
public interface NodeMetrics {

    /**
     * Fetches metrics for an application. This call may be expensive.
     *
     * @param application the application to fetch metrics from
     */
    Collection<MetricValue> fetchMetrics(ApplicationId application);

    final class MetricValue {

        private final String hostname;
        private final String name;
        private long timestamp;
        private final float value;

        public MetricValue(String hostname, String name, long timestamp, float value) {
            this.hostname = hostname;
            this.name = name;
            this.timestamp = timestamp;
            this.value = value;
        }

        public String hostname() { return hostname; }
        public String name() { return name; }
        public long timestamp() { return timestamp; }
        public float value() { return value; }

        @Override
        public String toString() {
            return "metric value " + name + ": " + value + " at " + timestamp + " for " + hostname;
        }

    }

}
