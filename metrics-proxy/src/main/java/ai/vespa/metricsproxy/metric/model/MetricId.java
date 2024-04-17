// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil;
import com.yahoo.concurrent.CopyOnWriteHashMap;

import java.util.Map;
import java.util.Objects;

/**
 * @author gjoranv
 */
public class MetricId {

    private static final Map<String, MetricId> dictionary = new CopyOnWriteHashMap<>();
    public static final MetricId empty = toMetricId("");
    public final String id;
    private final String idForPrometheus;
    private MetricId(String id) {
        this.id = id;
        idForPrometheus = PrometheusUtil.sanitize(id);
    }

    public static MetricId toMetricId(String id) {
        return dictionary.computeIfAbsent(id, MetricId::new);
    }
    public String getIdForPrometheus() { return idForPrometheus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MetricId metricId = (MetricId) o;
        return Objects.equals(id, metricId.id);
    }

    @Override
    public int hashCode() { return Objects.hash(id); }

    @Override
    public String toString() { return id; }

}
