package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.deployment.RunId;

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

    /** Attempts to run the given locked step in the given run, and returns its new status. */
    Step.Status run(LockedStep step, RunId id);

}

