// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.function.Function;

/**
 * Recent activity in an application.
 *
 * @author mpolden
 */
public class ApplicationActivity {

    public static final ApplicationActivity none = new ApplicationActivity(Optional.empty(), Optional.empty(),
                                                                           OptionalDouble.empty(),
                                                                           OptionalDouble.empty());

    private final Optional<Instant> lastQueried;
    private final Optional<Instant> lastWritten;
    private final OptionalDouble lastQueriesPerSecond;
    private final OptionalDouble lastWritesPerSecond;

    private ApplicationActivity(Optional<Instant> lastQueried, Optional<Instant> lastWritten,
                                OptionalDouble lastQueriesPerSecond, OptionalDouble lastWritesPerSecond) {
        this.lastQueried = Objects.requireNonNull(lastQueried, "lastQueried must be non-null");
        this.lastWritten = Objects.requireNonNull(lastWritten, "lastWritten must be non-null");
        this.lastQueriesPerSecond = Objects.requireNonNull(lastQueriesPerSecond, "lastQueriesPerSecond must be non-null");
        this.lastWritesPerSecond = Objects.requireNonNull(lastWritesPerSecond, "lastWritesPerSecond must be non-null");
    }

    /** The last time any deployment in this was queried */
    public Optional<Instant> lastQueried() {
        return lastQueried;
    }

    /** The last time any deployment in this was written */
    public Optional<Instant> lastWritten() {
        return lastWritten;
    }

    /** Query rate the last time this was queried */
    public OptionalDouble lastQueriesPerSecond() {
        return lastQueriesPerSecond;
    }

    /** Write rate the last time this was written */
    public OptionalDouble lastWritesPerSecond() {
        return lastWritesPerSecond;
    }

    public static ApplicationActivity from(Collection<Deployment> deployments) {
        Optional<DeploymentActivity> lastActivityByQuery = lastActivityBy(DeploymentActivity::lastQueried, deployments);
        Optional<DeploymentActivity> lastActivityByWrite = lastActivityBy(DeploymentActivity::lastWritten, deployments);
        if (!lastActivityByQuery.isPresent() && !lastActivityByWrite.isPresent()) {
            return none;
        }
        return new ApplicationActivity(lastActivityByQuery.flatMap(DeploymentActivity::lastQueried),
                                       lastActivityByWrite.flatMap(DeploymentActivity::lastWritten),
                                       lastActivityByQuery.map(DeploymentActivity::lastQueriesPerSecond)
                                                          .orElseGet(OptionalDouble::empty),
                                       lastActivityByWrite.map(DeploymentActivity::lastWritesPerSecond)
                                                          .orElseGet(OptionalDouble::empty));
    }

    private static Optional<DeploymentActivity> lastActivityBy(Function<DeploymentActivity, Optional<Instant>> field,
                                                               Collection<Deployment> deployments) {
        return deployments.stream()
                          .map(Deployment::activity)
                          .filter(activity -> field.apply(activity).isPresent())
                          .max(Comparator.comparing(activity -> field.apply(activity).get()));
    }

}
