// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.node;

import java.util.Collection;

/**
 * Interface to retrieve metrics on (tenant) nodes.
 *
 * @author bratseth
 */
public interface NodeMetrics {

    /**
     * Fetches node metrics for a node. This call may be expensive.
     *
     * @param hostname the hostname of the node to fetch metrics from
     */
    Collection<Metric> fetchMetrics(String hostname);

    final class Metric {

        private String name;
        private double value;

        public Metric(String name, double value) {
            this.name = name;
            this.value = value;
        }

        public String name() { return name; }

        public double value() { return value; }

    }

}
