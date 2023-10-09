// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class ServiceIdDimensionProcessorTest {
    private static final DimensionId NEW_ID_DIMENSION = toDimensionId(SERVICE_ID);

    @Test
    public void new_service_id_is_added_when_internal_service_id_exists() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        builder.putDimension(toDimensionId(INTERNAL_SERVICE_ID), "service");

        var processor = new ServiceIdDimensionProcessor();
        processor.process(builder);

        assertTrue(builder.getDimensionIds().contains(NEW_ID_DIMENSION));
        assertEquals("service", builder.getDimensionValue(NEW_ID_DIMENSION));
    }

    @Test
    public void new_service_id_is_not_added_when_internal_service_id_is_null() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));

        var processor = new ServiceIdDimensionProcessor();
        processor.process(builder);

        assertFalse(builder.getDimensionIds().contains(NEW_ID_DIMENSION));
    }

}
