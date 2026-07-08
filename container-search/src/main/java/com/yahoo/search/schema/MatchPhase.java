// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Information about the match-phase ranking of a rank profile in a schema.
 *
 * @author bratseth
 */
public class MatchPhase {

    private final OptionalLong maxHits;
    private final OptionalLong totalMaxHits;

    public MatchPhase(Builder builder) {
        this.maxHits = builder.maxHits;
        this.totalMaxHits = builder.totalMaxHits;
    }

    /** Returns the max hits per node to aim for producing in the match phase. */
    public OptionalLong maxHits() { return maxHits; }

    /** Returns the max hits to aim for producing across all nodes in the match phase. */
    public OptionalLong totalMaxHits() { return totalMaxHits; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof MatchPhase other)) return false;
        if ( ! other.maxHits.equals(this.maxHits)) return false;
        if ( ! other.totalMaxHits.equals(this.totalMaxHits)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxHits, totalMaxHits);
    }

    @Override
    public String toString() {
        var b = new StringBuilder("match phase ranking");
        maxHits.ifPresent(count -> b.append(" with " + count + " max hits per node"));
        totalMaxHits.ifPresent(count -> b.append(" with " + count + " max hits in total"));
        return b.toString();
    }

    public static class Builder {

        private OptionalLong maxHits = OptionalLong.empty();
        private OptionalLong totalMaxHits = OptionalLong.empty();

        public Builder setMaxHits(long maxHits) {
            this.maxHits = OptionalLong.of(maxHits);
            return this;
        }

        public Builder setTotalMaxHits(long totalMaxHits) {
            this.totalMaxHits = OptionalLong.of(totalMaxHits);
            return this;
        }

        public MatchPhase build() {
            return new MatchPhase(this);
        }

    }

}
