// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.predicate;

import com.yahoo.api.annotations.Beta;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Configuration for a {@link PredicateIndexBuilder}/{@link PredicateIndex} instance.
 *
 * @author bjorncs
 */
@Beta
public class Config {

    public final int arity;
    public final long lowerBound;
    public final long upperBound;
    public final boolean useConjunctionAlgorithm;

    private Config(int arity, long lowerBound, long upperBound, boolean useConjunctionAlgorithm) {
        this.arity = arity;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.useConjunctionAlgorithm = useConjunctionAlgorithm;
    }

    public void writeToOutputStream(DataOutputStream out) throws IOException {
        out.writeInt(arity);
        out.writeLong(lowerBound);
        out.writeLong(upperBound);
        out.writeBoolean(useConjunctionAlgorithm);
    }

    public static Config fromInputStream(DataInputStream in) throws IOException {
        int arity = in.readInt();
        long lowerBound = in.readLong();
        long upperBound = in.readLong();
        boolean useConjunctionAlgorithm = in.readBoolean();
        return new Config(arity, lowerBound, upperBound, useConjunctionAlgorithm);
    }

    public static class Builder {
        private int arity = 8;
        private long lowerBound = Long.MIN_VALUE;
        private long upperBound = Long.MAX_VALUE;
        private boolean useConjunctionAlgorithm = false;

        public Builder setArity(int arity) {
            this.arity = arity;
            return this;
        }

        public Builder setLowerBound(long lowerBound) {
            this.lowerBound = lowerBound;
            return this;
        }

        public Builder setUpperBound(long upperBound) {
            this.upperBound = upperBound;
            return this;
        }

        public Builder setUseConjunctionAlgorithm(boolean enabled) {
            this.useConjunctionAlgorithm = enabled;
            return this;
        }

        public Config build() {
            return new Config(arity, lowerBound, upperBound, useConjunctionAlgorithm);
        }

    }

}
