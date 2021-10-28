// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import java.util.Objects;

/**
 * @author gjoranv
 */
public class ServiceId {

    public final String id;
    private ServiceId(String id) { this.id = id; }

    public static ServiceId toServiceId(String id) { return new ServiceId(id); }

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
