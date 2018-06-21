package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import static com.yahoo.vespa.hosted.controller.deployment.Step.Status.succeeded;

/**
 * Executor which runs given {@link Step}s, for given {@link ApplicationId} and {@link JobType} combinations.
 *
 * @author jonmv
 */
public class StepRunner {

    /** Returns the new status of the given step for the implied job run. */
    Step.Status run(Step step, ApplicationId application, JobType jobType) {
        switch (step) {
            default: return succeeded;
        }
    }

}
