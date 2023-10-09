// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.core;

import ai.vespa.metricsproxy.metric.model.Dimension;
import ai.vespa.metricsproxy.metric.model.DimensionId;
import ai.vespa.metricsproxy.metric.model.MetricId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ConfiguredMetric {
    private final MetricId name;
    private final String description;
    private final MetricId outputname;
    private final List<Dimension> dimension;
    public ConfiguredMetric(ConsumersConfig.Consumer.Metric m) {
        name = MetricId.toMetricId(m.name());
        outputname = MetricId.toMetricId(m.outputname());
        description = m.description();
        dimension = new ArrayList<>(m.dimension().size());
        m.dimension().forEach(d -> dimension.add(new Dimension(DimensionId.toDimensionId(d.key()), d.value())));
    }
    public MetricId id() { return name; }
    public MetricId outputname() { return outputname; }
    public String description() { return description; }
    public List<Dimension> dimension() { return dimension; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfiguredMetric that = (ConfiguredMetric) o;
        return name.equals(that.name) && description.equals(that.description) && outputname.equals(that.outputname) && dimension.equals(that.dimension);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, outputname, dimension);
    }
}
