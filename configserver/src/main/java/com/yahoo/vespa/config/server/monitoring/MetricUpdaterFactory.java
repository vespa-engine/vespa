// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.config.server.monitoring;

import java.util.Map;

/**
 * A factory for creating metric updates with a given context.
 *
 * @author Ulf Lilleengen
 * @since 5.15
 */
public interface MetricUpdaterFactory {
    MetricUpdater getOrCreateMetricUpdater(Map<String, String> dimensions);
}
