// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Runs maintenance operations for the controller database, such as removing stale entries.
 *
 * @author mpolden
 */
public class DatabaseMaintainer extends Maintainer {

    public DatabaseMaintainer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    protected void maintain() {
        removePullRequestInstances();
    }

    /** Pull request instances are no longer created. This removes all existing entries */
    // TODO: Remove after 2018-11-01
    // TODO: Remove ApplicationList#notPullRequest and its usages
    private void removePullRequestInstances() {
        List<Application> pullRequestInstances = controller().applications().asList().stream()
                                                             .filter(a -> a.id().instance().value().matches("^(default-pr)?\\d+$"))
                                                             .collect(Collectors.toList());

        pullRequestInstances.forEach(application -> {
            controller().applications().lockIfPresent(application.id(), (lockedApplication) -> controller().curator().removeApplication(lockedApplication.get().id()));
        });
    }

}
