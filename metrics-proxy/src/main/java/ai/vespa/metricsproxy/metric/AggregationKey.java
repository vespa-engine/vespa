// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric;

import ai.vespa.metricsproxy.metric.model.ConsumerId;
import ai.vespa.metricsproxy.metric.model.DimensionId;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @author gjoranv
 */
public final class AggregationKey {

    private final Map<DimensionId, String> dimensions;
    private final Set<ConsumerId> consumers;

    public AggregationKey(Map<DimensionId, String> dimensions, Set<ConsumerId> consumers) {
        this.dimensions = dimensions;
        this.consumers = consumers;
    }

    public Map<DimensionId, String> getDimensions() { return Collections.unmodifiableMap(dimensions); }

    public Set<ConsumerId> getConsumers() { return Collections.unmodifiableSet(consumers); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AggregationKey that = (AggregationKey) o;
        return Objects.equals(dimensions, that.dimensions) &&
                Objects.equals(consumers, that.consumers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dimensions, consumers);
    }
}
