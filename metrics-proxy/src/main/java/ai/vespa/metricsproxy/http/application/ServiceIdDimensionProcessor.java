package ai.vespa.metricsproxy.http.application;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;
import ai.vespa.metricsproxy.metric.model.processing.MetricsProcessor;

import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.INTERNAL_SERVICE_ID;
import static ai.vespa.metricsproxy.metric.dimensions.PublicDimensions.SERVICE_ID;
import static ai.vespa.metricsproxy.metric.model.DimensionId.toDimensionId;

/**
 * Copies the value of the internally used 'instance' dimension to the more aptly named 'serviceId'.
 *
 * @author gjoranv
 */
public class ServiceIdDimensionProcessor implements MetricsProcessor {

    @Override
    public void process(MetricsPacket.Builder builder) {
        String serviceIdValue = builder.getDimensionValue(toDimensionId(INTERNAL_SERVICE_ID));
        if (serviceIdValue != null)
            builder.putDimension(toDimensionId(SERVICE_ID), serviceIdValue);
    }

}
