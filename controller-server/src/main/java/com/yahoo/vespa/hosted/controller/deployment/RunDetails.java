package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.api.ActivateResult;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.PrepareResponse;

/**
 * Contains details about a deployment job run.
 *
 * @author jonmv
 */
public class RunDetails {

    private final PrepareResponse deploymentResult;
    private final String convergenceLog;
    private final String testLog;

    public RunDetails(PrepareResponse deploymentResult, String convergenceLog, String testLog) {
        this.deploymentResult = deploymentResult;
        this.convergenceLog = convergenceLog;
        this.testLog = testLog;
    }

}
