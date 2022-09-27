// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.metricsproxy.metric.model;

import java.util.Objects;

/**
 * @author gjoranv
 */
public class ConsumerId {

    public final String id;
    private ConsumerId(String id) {
        this.id = Objects.requireNonNull(id);
    }

    public static ConsumerId toConsumerId(String id) { return new ConsumerId(id); }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumerId that = (ConsumerId) o;
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
