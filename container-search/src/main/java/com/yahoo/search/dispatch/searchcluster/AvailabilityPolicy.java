// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.dispatch.searchcluster;

import com.yahoo.vespa.config.search.DispatchConfig;

/**
 * Polcies for group availability.
 *
 * @author bratseth
 */
public record AvailabilityPolicy(boolean prioritizeAvailability, double minActiveDocsPercentage) {

    public static AvailabilityPolicy from(DispatchConfig config) {
        return new AvailabilityPolicy(config.prioritizeAvailability(), config.minActivedocsPercentage());
    }

}
