// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.support.access;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** An (immutable) change in support access, recording what change was made, when, and by whom. */
public class SupportAccessChange {
    private final Instant madeAt;
    private final Optional<Instant> accessAllowedUntil;
    private final String changedBy;

    public SupportAccessChange(Optional<Instant> accessAllowedUntil, Instant changeTime, String changedBy) {
        this.madeAt = changeTime;
        this.accessAllowedUntil = accessAllowedUntil;
        this.changedBy = changedBy;
    }

    public Instant changeTime() {
        return madeAt;
    }

    public Optional<Instant> accessAllowedUntil() {
        return accessAllowedUntil;
    }

    public String madeBy() {
        return changedBy;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupportAccessChange that = (SupportAccessChange) o;
        return madeAt.equals(that.madeAt) && accessAllowedUntil.equals(that.accessAllowedUntil) && changedBy.equals(that.changedBy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(madeAt, accessAllowedUntil, changedBy);
    }

    @Override
    public String toString() {
        return "SupportAccessChange{" +
                "madeAt=" + madeAt +
                ", accessAllowedUntil=" + accessAllowedUntil +
                ", changedBy='" + changedBy + '\'' +
                '}';
    }
}
