// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import com.yahoo.container.jdisc.state.HostLifeGatherer;
import org.junit.Test;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.APPLICATION_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class PublicDimensionsProcessorTest {

    @Test
    public void blocklisted_dimensions_are_removed() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .putDimension(toDimensionId("applicationName"), "");

        var processor = new PublicDimensionsProcessor();
        processor.process(builder);
        assertTrue(builder.getDimensionIds().isEmpty());
    }

    @Test
    public void public_dimensions_are_retained() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .putDimension(toDimensionId(APPLICATION_ID), "app");

        var processor = new PublicDimensionsProcessor();
        processor.process(builder);
        assertEquals(1, builder.getDimensionIds().size());
        assertEquals(toDimensionId(APPLICATION_ID), builder.getDimensionIds().iterator().next());
    }

    @Test
    public void vespaVersion_is_removed_for_non_host_life_services() {
        var builder = new MetricsPacket.Builder(toServiceId("searchnode"))
                .putDimension(toDimensionId("vespaVersion"), "8.0.0");

        var processor = new PublicDimensionsProcessor();
        processor.process(builder);
        assertTrue(builder.getDimensionIds().isEmpty());
    }

    @Test
    public void vespaVersion_is_retained_on_host_life() {
        var builder = new MetricsPacket.Builder(toServiceId(HostLifeGatherer.SERVICE_NAME))
                .putDimension(toDimensionId("vespaVersion"), "8.0.0")
                .putDimension(toDimensionId("applicationName"), "should-be-removed");

        var processor = new PublicDimensionsProcessor();
        processor.process(builder);
        assertEquals(1, builder.getDimensionIds().size());
        assertTrue(builder.getDimensionIds().contains(toDimensionId("vespaVersion")));
        assertFalse(builder.getDimensionIds().contains(toDimensionId("applicationName")));
    }

}
