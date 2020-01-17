package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.CLUSTER_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_CLUSTER_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_CLUSTER_TYPE;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Replaces the current cluster ID dimension value with "clustertype/clusterid".
 *
 * @author gjoranv
 */
public class ClusterIdDimensionProcessor implements MetricsProcessor {

    @Override
    public void process(MetricsPacket.Builder builder) {
        String clusterType = emptyIfNull(builder.getDimensionValue(toDimensionId(INTERNAL_CLUSTER_TYPE)));
        String clusterId = emptyIfNull(builder.getDimensionValue(toDimensionId(INTERNAL_CLUSTER_ID)));

        String newClusterId;
        if (! clusterType.isEmpty() && ! clusterId.isEmpty())
            newClusterId = clusterType + "/" + clusterId;
        else if (! clusterType.isEmpty())
            newClusterId = clusterType;
        else if (! clusterId.isEmpty())
            newClusterId = clusterId;
        else
            return;  // Both type and id were null or empty

        builder.putDimension(toDimensionId(CLUSTER_ID), newClusterId);
    }

    private String emptyIfNull(String s) {
        return s == null ? "" : s;
    }
}
