// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.flags;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * @author hakonhall
 */
public class FlagId implements Comparable<FlagId> {
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-zA-Z0-9][a-zA-Z0-9._-]*$");

    private final String id;

    public FlagId(String id) {
        if (!ID_PATTERN.matcher(id).find()) {
            throw new IllegalArgumentException("Not a valid FlagId: '" + id + "'");
        }

        this.id = id;
    }

    @Override
    public int compareTo(FlagId that) {
        return this.id.compareTo(that.id);
    }

    @Override
    public String toString() {
        return id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FlagId flagId = (FlagId) o;
        return Objects.equals(id, flagId.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
