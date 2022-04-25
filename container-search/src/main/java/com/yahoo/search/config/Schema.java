// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.config;

import com.yahoo.api.annotations.Beta;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Information about a schema which is part of the application running this.
 *
 * This is immutable.
 *
 * @author bratseth
 */
@Beta
public class Schema {

    private final String name;
    private final Map<String, RankProfile> rankProfiles;

    private Schema(Builder builder) {
        this.name = builder.name;
        this.rankProfiles = Map.copyOf(builder.rankProfiles);
    }

    public String name() { return name; }
    public Map<String, RankProfile> rankProfiles() { return rankProfiles; }

    @Override
    public boolean equals(Object o) {
        if (o == this) return true;
        if ( ! (o instanceof Schema)) return false;
        Schema other = (Schema)o;
        if ( ! other.name.equals(this.name)) return false;
        if ( ! other.rankProfiles.equals(this.rankProfiles)) return false;
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, rankProfiles);
    }

    @Override
    public String toString() {
        return "schema '" + name + "'";
    }

    public static class Builder {

        private final String name;
        private final Map<String, RankProfile> rankProfiles = new HashMap<>();

        public Builder(String name) {
            this.name = Objects.requireNonNull(name);
        }

        public Builder add(RankProfile profile) {
            rankProfiles.put(profile.name(), Objects.requireNonNull(profile));
            return this;
        }

        public Schema build() {
            return new Schema(this);
        }

    }

}
