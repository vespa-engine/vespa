// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import ai.vespa.metricsproxy.metric.model.prometheus.PrometheusUtil;

import java.util.Objects;

/**
 * @author gjoranv
 */
public class ServiceId {

    public final String id;
    private final String idForPrometheus;
    private ServiceId(String id) {
        this.id = id;
        idForPrometheus = PrometheusUtil.sanitize(id);
    }

    public static ServiceId toServiceId(String id) { return new ServiceId(id); }

    public String getIdForPrometheus() { return idForPrometheus; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceId serviceId = (ServiceId) o;
        return Objects.equals(id, serviceId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return id;
    }

}
