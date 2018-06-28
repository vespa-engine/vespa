package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Contains details about a deployment job run.
 *
 * @author jonmv
 */
public class RunDetails {

    private final String deploymentLog;
    private final String convergenceLog;
    private final String testLog;

    public RunDetails(String deploymentLog, String convergenceLog, String testLog) {
        this.deploymentLog = deploymentLog;
        this.convergenceLog = convergenceLog;
        this.testLog = testLog;
    }

    public String getDeploymentLog() {
        return deploymentLog;
    }

    public String getConvergenceLog() {
        return convergenceLog;
    }

    public String getTestLog() {
        return testLog;
    }

}
