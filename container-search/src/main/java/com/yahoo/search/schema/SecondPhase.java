// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;

/**
 * Information about the second-phase ranking of a rank profile in a schema.
 *
 * @author bratseth
 */
public class SecondPhase {

    private final OptionalInt rerankCount;
    private final OptionalInt totalRerankCount;

    public SecondPhase(Builder builder) {
        this.rerankCount = builder.rerankCount;
        this.totalRerankCount = builder.totalRerankCount;
    }

    /** Returns the number of hits to rerank in second phase on each node, or empty to use the default. */
    public OptionalInt rerankCount() { return rerankCount; }

    /** Returns the number of hits to rerank in second phase across all nodes, or empty to use rerankCount. */
    public OptionalInt totalRerankCount() { return totalRerankCount; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof SecondPhase other)) return false;
        if ( ! other.rerankCount.equals(this.rerankCount)) return false;
        if ( ! other.totalRerankCount.equals(this.totalRerankCount)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(rerankCount, totalRerankCount);
    }

    @Override
    public String toString() {
        var b = new StringBuilder("second phase ranking");
        rerankCount.ifPresent(count -> b.append(" of " + count + " hits per node"));
        totalRerankCount.ifPresent(count -> b.append(" of " + count + " hits in total"));
        return b.toString();
    }

    public static class Builder {

        private OptionalInt rerankCount = OptionalInt.empty();
        private OptionalInt totalRerankCount = OptionalInt.empty();

        public Builder setRerankCount(int rerankCount) {
            this.rerankCount = OptionalInt.of(rerankCount);
            return this;
        }

        public Builder setTotalRerankCount(int totalRerankCount) {
            this.totalRerankCount = OptionalInt.of(totalRerankCount);
            return this;
        }

        public SecondPhase build() {
            return new SecondPhase(this);
        }

    }

}
