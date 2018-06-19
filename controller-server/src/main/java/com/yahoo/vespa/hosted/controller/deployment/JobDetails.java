package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.ActivateResult;

/**
 * Contains details about a deployment job run.
 *
 * @author jonmv
 */
public class JobDetails {

    private final ActivateResult deploymentResult;
    private final String convergenceLog;
    private final String testLog;

    public JobDetails(ActivateResult deploymentResult, String convergenceLog, String testLog) {
        this.deploymentResult = deploymentResult;
        this.convergenceLog = convergenceLog;
        this.testLog = testLog;
    }

}
