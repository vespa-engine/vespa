// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;
import java.util.function.Function;

/**
 * Recent activity in an application.
 *
 * @author mpolden
 */
public class ApplicationActivity {

    public static final ApplicationActivity none = new ApplicationActivity(Optional.empty(), Optional.empty());

    private final Optional<Instant> lastQueried;
    private final Optional<Instant> lastWritten;

    private ApplicationActivity(Optional<Instant> lastQueried, Optional<Instant> lastWritten) {
        this.lastQueried = lastQueried;
        this.lastWritten = lastWritten;
    }

    /** The last time any deployment in this was queried */
    public Optional<Instant> lastQueried() {
        return lastQueried;
    }

    /** The last time any deployment in this was written */
    public Optional<Instant> lastWritten() {
        return lastWritten;
    }

    public static ApplicationActivity from(Collection<Deployment> deployments) {
        Optional<Instant> lastQueried = lastActivity(deployments, DeploymentActivity::lastQueried);
        Optional<Instant> lastWritten = lastActivity(deployments, DeploymentActivity::lastWritten);
        if (!lastQueried.isPresent() && !lastWritten.isPresent()) {
            return none;
        }
        return new ApplicationActivity(lastQueried, lastWritten);
    }

    private static Optional<Instant> lastActivity(Collection<Deployment> deployments,
                                                  Function<DeploymentActivity, Optional<Instant>> activityField) {
        return deployments.stream()
                          .map(Deployment::activity)
                          .map(activityField)
                          .filter(Optional::isPresent)
                          .map(Optional::get)
                          .max(Comparator.naturalOrder());
    }

}
