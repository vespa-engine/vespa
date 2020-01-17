package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import org.junit.Test;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.CLUSTER_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_CLUSTER_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_CLUSTER_TYPE;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;
import static ai.vespa.metricsproxy.metric.model.ServiceId.toServiceId;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * @author gjoranv
 */
public class ClusterIdDimensionProcessorTest {
    private static final DimensionId NEW_ID_DIMENSION = toDimensionId(CLUSTER_ID);

    @Test
    public void cluster_id_is_replaced_with_type_and_id() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        builder.putDimension(toDimensionId(INTERNAL_CLUSTER_TYPE), "type") ;
        builder.putDimension(toDimensionId(INTERNAL_CLUSTER_ID), "id") ;

        assertEquals("type/id", newClusterId(builder));
    }

    @Test
    public void cluster_id_is_type_when_id_is_null() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        builder.putDimension(toDimensionId(INTERNAL_CLUSTER_TYPE), "type") ;

        assertEquals(newClusterId(builder), "type");
    }

    @Test
    public void cluster_id_is_id_when_type_is_null() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));
        builder.putDimension(toDimensionId(INTERNAL_CLUSTER_ID), "id") ;

        assertEquals(newClusterId(builder), "id");
    }

    @Test
    public void cluster_id_is_not_added_when_both_type_and_id_are_null() {
        var builder = new MetricsPacket.Builder(toServiceId("foo"));

        var processor = new ClusterIdDimensionProcessor();
        processor.process(builder);

        assertFalse(builder.getDimensionIds().contains(NEW_ID_DIMENSION));
    }

    private String newClusterId(MetricsPacket.Builder builder) {
        var processor = new ClusterIdDimensionProcessor();
        processor.process(builder);

        return builder.getDimensionValue(NEW_ID_DIMENSION);
    }

}
