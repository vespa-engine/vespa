// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model;

/**
 * Represents a set of affinities that can be expressed by a service. Currently only supports
 * CPU socket affinity.
 *
 * @author Ulf Lilleengen
 */
public class Affinity {

    private final int cpuSocket;

    private Affinity(int cpuSocket) {
        this.cpuSocket = cpuSocket;
    }

    public int cpuSocket() {
        return cpuSocket;
    }

    public static Affinity none() {
        return new Builder().build();
    }

    public static class Builder {
        private int cpuSocket = -1;
        public Builder cpuSocket(int cpuSocket) {
            this.cpuSocket = cpuSocket;
            return this;
        }

        public Affinity build() {
            return new Affinity(cpuSocket);
        }
    }

}
