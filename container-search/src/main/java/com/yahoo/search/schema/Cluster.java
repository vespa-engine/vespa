// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.api.annotations.Beta;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Information about the search aspects of a content cluster.
 *
 * @author bratseth
 */
@Beta
public class Cluster {

    private final String name;
    private final boolean isStreaming;
    private final Set<String> schemas;

    private Cluster(Builder builder) {
        this.name = builder.name;
        this.isStreaming = builder.isStreaming;
        this.schemas = Set.copyOf(builder.schemas);
    }

    public String name() { return name; }

    /** Returns true if this cluster uses streaming search. */
    public boolean isStreaming() { return isStreaming; }

    /** Returns the names of the subset of all schemas that are present in this cluster. */
    public Set<String> schemas() { return schemas; }

    @Override
    public boolean equals(Object o) {
        if ( ! (o instanceof Cluster other)) return false;
        if ( ! this.name.equals(other.name)) return false;
        if ( this.isStreaming != other.isStreaming()) return false;
        if ( ! this.schemas.equals(other.schemas)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, isStreaming, schemas);
    }

    @Override
    public String toString() { return "cluster '" + name + "'"; }

    public static class Builder {

        private final String name;
        private boolean isStreaming = false;
        private final Set<String> schemas = new HashSet<>();

        public Builder(String name) {
            this.name = name;
        }

        public Builder setStreaming(boolean isStreaming) {
            this.isStreaming = isStreaming;
            return this;
        }

        public Builder addSchema(String schema) {
            schemas.add(schema);
            return this;
        }

        public Cluster build() {
            return new Cluster(this);
        }

    }

}
