// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.metric;

/**
 * Application metrics aggregated across all deployments.
 *
 * @author bratseth
 */
public class ApplicationMetrics {

    private final double queryServiceQuality;
    private final double writeServiceQuality;

    public ApplicationMetrics(double queryServiceQuality, double writeServiceQuality) {
        this.queryServiceQuality = queryServiceQuality;
        this.writeServiceQuality = writeServiceQuality;
    }

    /**
     * Returns the quality of service for queries as a number between 1 (perfect) and 0 (none)
     */
    public double queryServiceQuality() {
        return queryServiceQuality;
    }

    /**
     * Returns the quality of service for writes as a number between 1 (perfect) and 0 (none)
     */
    public double writeServiceQuality() {
        return writeServiceQuality;
    }

}
