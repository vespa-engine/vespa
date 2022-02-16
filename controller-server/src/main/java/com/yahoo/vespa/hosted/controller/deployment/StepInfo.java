// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Information about a step. Immutable.
 *
 * @author hakonhall
 */
public class StepInfo {

    private final Step step;
    private final Step.Status status;
    private final Optional<Instant> startTime;

    public static StepInfo initial(Step step) { return new StepInfo(step, Step.Status.unfinished, Optional.empty()); }

    public StepInfo(Step step, Step.Status status, Optional<Instant> startTime) {
        this.step = step;
        this.status = status;
        this.startTime = startTime;
    }

    public Step step() { return step; }
    public Step.Status status() { return status; }
    public Optional<Instant> startTime() { return startTime; }

    /** Returns a copy of this, but with the given status. */
    public StepInfo with(Step.Status status) { return new StepInfo(step, status, startTime); }

    /** Returns a copy of this, but with the given start timestamp. */
    public StepInfo with(Instant startTimestamp) { return new StepInfo(step, status, Optional.of(startTimestamp)); }

    @Override
    public String toString() {
        return "StepInfo{" +
                "step=" + step +
                ", status=" + status +
                ", startTime=" + startTime +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StepInfo stepInfo = (StepInfo) o;
        return step == stepInfo.step &&
                status == stepInfo.status &&
                Objects.equals(startTime, stepInfo.startTime);
    }

    @Override
    public int hashCode() {
        return Objects.hash(step, status, startTime);
    }

}
