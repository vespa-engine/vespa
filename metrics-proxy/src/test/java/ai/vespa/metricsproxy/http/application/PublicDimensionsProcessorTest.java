package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import static ai.vespa.metricsproxy.http.application.PublicDimensionsProcessor.getPublicDimensions;
import static ai.vespa.metricsproxy.http.application.PublicDimensionsProcessor.toDimensionIds;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.APPLICATION_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.commonDimensions;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.publicDimensions;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author gjoranv
 */
public class PublicDimensionsProcessorTest {

    @Test
    public void non_public_dimensions_are_removed() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .putDimension(toDimensionId("a"), "");

        var processor = new PublicDimensionsProcessor(10);
        processor.process(builder);
        assertTrue(builder.getDimensionIds().isEmpty());
    }

    @Test
    public void public_dimensions_are_retained() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"))
                .putDimension(toDimensionId(APPLICATION_ID), "app");

        var processor = new PublicDimensionsProcessor(10);
        processor.process(builder);
        assertEquals(1, builder.getDimensionIds().size());
        assertEquals(toDimensionId(APPLICATION_ID), builder.getDimensionIds().iterator().next());
    }

    @Test
    public void common_dimensions_have_priority_when_there_are_too_many() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        getPublicDimensions()
                .forEach(dimensionId -> builder.putDimension(dimensionId, dimensionId.id));
        assertEquals(publicDimensions.size(), builder.getDimensionIds().size());

        var processor = new PublicDimensionsProcessor(commonDimensions.size());
        processor.process(builder);

        var includedDimensions = builder.getDimensionIds();
        assertEquals(commonDimensions.size(), includedDimensions.size());

        toDimensionIds(commonDimensions).forEach(commonDimension ->
                assertTrue(includedDimensions.contains(commonDimension)));
    }

}
