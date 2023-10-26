// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.admin.monitoring;

/**
 * Interface for monitoring services
 *
 * @author hmusum
 */
public interface Monitoring {

    /**
     * @return Snapshot interval in minutes
     */
    Integer getInterval();

    /**
     * @return Snapshot interval in seconds.
     */
    Integer getIntervalSeconds();

    /**
     * @return the monitoring cluster name
     */
    String getClustername();

}
