// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import com.yahoo.concurrent.CopyOnWriteHashMap;

import java.util.Map;
import java.util.Objects;

/**
 * @author gjoranv
 */
public final class DimensionId {

    private static final Map<String, DimensionId> dictionary = new CopyOnWriteHashMap<>();
    public final String id;
    private DimensionId(String id) { this.id = id; }

    public static DimensionId toDimensionId(String id) {
        return dictionary.computeIfAbsent(id, key -> new DimensionId(key));
    }

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
        return id;
    }
}
