// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.deployment.RetriggerEntry;
import com.yahoo.vespa.hosted.controller.deployment.Run;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Trigger any jobs that are marked for re-triggering to effectuate some other change, e.g. a change in access to a
 * deployment's nodes.
 *
 * @author tokle
 */
public class RetriggerMaintainer extends ControllerMaintainer {

    private static final Logger logger = Logger.getLogger(RetriggerMaintainer.class.getName());

    public RetriggerMaintainer(Controller controller, Duration interval) {
        super(controller, interval);
    }

    @Override
    protected double maintain() {
        try (var lock = controller().curator().lockDeploymentRetriggerQueue()) {
            List<RetriggerEntry> retriggerEntries = controller().curator().readRetriggerEntries();

            // Trigger all jobs that still need triggering and is not running
            retriggerEntries.stream()
                    .filter(this::needsTrigger)
                    .filter(entry -> readyToTrigger(entry.jobId()))
                    .forEach(entry -> controller().applications().deploymentTrigger().reTrigger(entry.jobId().application(), entry.jobId().type(),
                                                                                                "re-triggered by " + getClass().getSimpleName()));

            // Remove all jobs that has succeeded with the required job run and persist the list
            List<RetriggerEntry> remaining = retriggerEntries.stream()
                    .filter(this::needsTrigger)
                    .toList();
            controller().curator().writeRetriggerEntries(remaining);
        } catch (Exception e) {
            logger.log(Level.WARNING, "Exception while triggering jobs", e);
            return 1.0;
        }
        return 0.0;
    }

    /** Returns true if a job is ready to run, i.e. is currently not running */
    private boolean readyToTrigger(JobId jobId) {
        Optional<Run> existingRun = controller().jobController().active(jobId.application()).stream()
                .filter(run -> run.id().type().equals(jobId.type()))
                .findFirst();
        return existingRun.isEmpty();
    }

    /** Returns true of job needs triggering. I.e. the job has not run since the queue item was last run */
    private boolean needsTrigger(RetriggerEntry entry) {
        return controller().jobController().lastCompleted(entry.jobId())
                .filter(run -> run.id().number() < entry.requiredRun())
                .isPresent();
    }
}
