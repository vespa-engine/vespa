// Copyright 2020 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import java.util.Objects;

/**
 * @author gjoranv
 */
public class DimensionId {

    public final String id;
    private DimensionId(String id) { this.id = id; }

    public static DimensionId toDimensionId(String id) { return new DimensionId(id); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DimensionId that = (DimensionId) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "DimensionId{" +
                "id='" + id + '\'' +
                '}';
    }
}
