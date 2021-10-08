// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

import java.util.Optional;

/**
 * Advances a given job run by running the appropriate {@link Step}s, based on their current status.
 *
 * When an attempt is made to advance a given job, a lock for that job (application and type) is
 * taken, and released again only when the attempt finishes. Multiple other attempts may be made in
 * the meantime, but they should give up unless the lock is promptly acquired.
 *
 * @author jonmv
 */
public interface StepRunner {

    /** Attempts to run the given step in the given run, and returns the new status of the run, if the step completed. */
    Optional<RunStatus> run(LockedStep step, RunId id);

}

