// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Recent activity in a deployment.
 *
 * @author mpolden
 */
public class DeploymentActivity {

    /** Query rates at or below this threshold indicate inactivity */
    private static final double inactivityThreshold = 0;

    public static final DeploymentActivity none = new DeploymentActivity(Optional.empty(), Optional.empty(),
                                                                         OptionalDouble.empty(),
                                                                         OptionalDouble.empty());

    private final Optional<Instant> lastQueried;
    private final Optional<Instant> lastWritten;
    private final OptionalDouble lastQueriesPerSecond;
    private final OptionalDouble lastWritesPerSecond;

    private DeploymentActivity(Optional<Instant> lastQueried, Optional<Instant> lastWritten,
                               OptionalDouble lastQueriesPerSecond, OptionalDouble lastWritesPerSecond) {
        this.lastQueried = Objects.requireNonNull(lastQueried, "lastQueried must be non-null");
        this.lastWritten = Objects.requireNonNull(lastWritten, "lastWritten must be non-null");
        this.lastQueriesPerSecond = Objects.requireNonNull(lastQueriesPerSecond, "lastQueriesPerSecond must be non-null");
        this.lastWritesPerSecond = Objects.requireNonNull(lastWritesPerSecond, "lastWritesPerSecond must be non-null");
    }

    /** The last time this deployment received queries (search) */
    public Optional<Instant> lastQueried() {
        return lastQueried;
    }

    /** The last time this deployment received writes (feed) */
    public Optional<Instant> lastWritten() {
        return lastWritten;
    }

    /** Query rate the last time this deployment received queries (search) */
    public OptionalDouble lastQueriesPerSecond() {
        return lastQueriesPerSecond;
    }

    /** Write rate the last time this deployment received writes (feed) */
    public OptionalDouble lastWritesPerSecond() {
        return lastWritesPerSecond;
    }

    /** Record activity using given metrics */
    public DeploymentActivity recordAt(Instant instant, DeploymentMetrics metrics) {
        return new DeploymentActivity(activityAt(instant, lastQueried, metrics.queriesPerSecond()),
                                      activityAt(instant, lastWritten, metrics.writesPerSecond()),
                                      activeRate(metrics.queriesPerSecond(), lastQueriesPerSecond),
                                      activeRate(metrics.writesPerSecond(), lastWritesPerSecond));
    }

    public static DeploymentActivity create(Optional<Instant> queriedAt, Optional<Instant> writtenAt,
                                            OptionalDouble lastQueriesPerSecond, OptionalDouble lastWritesPerSecond) {
        if (!queriedAt.isPresent() && !writtenAt.isPresent()) {
            return none;
        }
        return new DeploymentActivity(queriedAt, writtenAt, lastQueriesPerSecond, lastWritesPerSecond);
    }

    public static DeploymentActivity create(Optional<Instant> queriedAt, Optional<Instant> writtenAt) {
        return create(queriedAt, writtenAt, OptionalDouble.empty(), OptionalDouble.empty());
    }

    private static OptionalDouble activeRate(double newRate, OptionalDouble oldRate) {
        return newRate > inactivityThreshold ? OptionalDouble.of(newRate) : oldRate;
    }

    private static Optional<Instant> activityAt(Instant newInstant, Optional<Instant> oldInstant, double rate) {
        return rate > inactivityThreshold ? Optional.of(newInstant) : oldInstant;
    }

}
