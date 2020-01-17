package ai.vespa.metricsproxy.metric.model.processing;

import ai.vespa.metricsproxy.metric.model.MetricsPacket;

import java.util.Arrays;

/**
 * Interface for classes that make amendments to a metrics packet builder.
 * Includes a utility method to apply a list of processors to a metrics packet.
 *
 * @author gjoranv
 */
public interface MetricsProcessor {

    /**
     * Processes the metrics packet builder in-place.
     */
    void process(MetricsPacket.Builder builder);


    /**
     * Helper method to apply a list of processors to a metrics packet builder.
     * Returns the metrics packet builder (which has been processed in-place) for
     * convenient use in stream processing.
     */
    static MetricsPacket.Builder applyProcessors(MetricsPacket.Builder builder, MetricsProcessor... processors) {
        Arrays.stream(processors).forEach(processor -> processor.process(builder));
        return builder;
    }

}
