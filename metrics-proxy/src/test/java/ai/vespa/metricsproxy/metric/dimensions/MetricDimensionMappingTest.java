// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.dimensions;

import org.junit.Test;

import java.util.Set;

import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;

/**
 * @author onur
 */
public class MetricDimensionMappingTest {

    @Test
    public void uses_default_for_unmapped_services_and_explicit_mapping_for_listed_services() {
        MetricDimensionMapping mapping = new MetricDimensionMapping(
                new MetricDimensionMappingConfig.Builder()
                        .defaultDimension("host")
                        .defaultDimension("parentHostname")
                        .mapping(new MetricDimensionMappingConfig.Mapping.Builder()
                                .service("host_life")
                                .dimension("host")
                                .dimension("parentHostname")
                                .dimension("osVersion"))
                        .build());

        assertEquals(Set.of(toDimensionId("host"), toDimensionId("parentHostname"), toDimensionId("osVersion")),
                mapping.allowedFor(toServiceId("host_life")));

        // A service not in the mapping falls back to the default dimensions.
        assertEquals(Set.of(toDimensionId("host"), toDimensionId("parentHostname")),
                mapping.allowedFor(toServiceId("vespa.node")));

        // The managed set is the union of default + all per-service dimensions.
        assertEquals(Set.of(toDimensionId("host"), toDimensionId("parentHostname"), toDimensionId("osVersion")),
                mapping.managedDimensions());
    }

}
