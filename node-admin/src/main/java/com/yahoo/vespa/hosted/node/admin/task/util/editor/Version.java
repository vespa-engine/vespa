// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.node.admin.task.util.editor;

import javax.annotation.concurrent.Immutable;
import java.util.Objects;

/**
 * Represents a snapshot of the TextBuffer, between two edits (or the initial or final state)
 *
 * @author hakon
 */
@Immutable
public class Version {
    private final int version;

    Version() {
        this(0);
    }

    private Version(int version) {
        this.version = version;
    }

    public boolean isBefore(Version that) {
        return version < that.version;
    }

    public int asInt() {
        return version;
    }

    public Version next() {
        return new Version(version + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version that = (Version) o;
        return version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }

    @Override
    public String toString() {
        return String.valueOf(version);
    }
}
