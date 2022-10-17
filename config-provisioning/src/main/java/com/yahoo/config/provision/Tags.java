// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * A deployment may have a list of tags associated with it. Config files may have variants for these tags similar
 * to how they may have variants for instance and zone.
 *
 * @author bratseth
 */
public class Tags {

    private final Set<String> tags;

    public Tags(Set<String> tags) {
        this.tags = Set.copyOf(tags);
    }

    public boolean contains(String tag) {
        return tags.contains(tag);
    }

    public boolean intersects(Tags other) {
        return this.tags.stream().anyMatch(other::contains);
    }

    public boolean isEmpty() { return tags.isEmpty(); }

    public boolean containsAll(Tags other) { return tags.containsAll(other.tags); }

    /** Returns this as a space-separated string which can be used to recreate this by calling fromString(). */
    public String asString() {
        return tags.stream().sorted().collect(Collectors.joining(" "));
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) return true;
        if (other == null || other.getClass() != getClass()) return false;
        return tags.equals(((Tags)other).tags);
    }

    @Override
    public int hashCode() {
        return tags.hashCode();
    }

    public static Tags empty() { return new Tags(Set.of()); }

    /**
     * Creates this from a space-separated string or null. */
    public static Tags fromString(String tagsString) {
        if (tagsString == null || tagsString.isBlank()) return empty();
        return new Tags(Set.of(tagsString.trim().split(" +")));
    }

}
