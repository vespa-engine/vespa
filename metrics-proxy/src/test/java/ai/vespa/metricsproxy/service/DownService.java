// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.service;

import ai.vespa.metricsproxy.metric.HealthMetric;
import ai.vespa.metricsproxy.metric.Metrics;

/**
 * @author gjoranv
 */
public class DownService extends VespaService {
    public static final String NAME = "down-service";

    private final HealthMetric healthMetric;

    public DownService(HealthMetric healthMetric) {
        super(NAME, "");
        this.healthMetric = healthMetric;
    }

    @Override
    public Metrics getMetrics() {
        return new Metrics();
    }

   @Override
   public HealthMetric getHealth() {
        return healthMetric;
   }
}
