package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Outcomes of jobs run by a {@link JobController}.
 *
 * @author jonmv
 */
public enum RunResult {

    /** Deployment of the real application was rejected due to missing capacity. */
    outOfCapacity,

    /** Deployment of the real application was rejected. */
    deploymentFailed,

    /** Installation of the real application timed out. */
    installationFailed,

    /** Real application was deployed, but the tester application was not. */
    testError,

    /** Real application was deployed, but the tests failed. */
    testFailure,

    /** Deployment and tests completed with great success! */
    success,

    /** Job completed abnormally, due to user intervention or unexpected system error. */
    aborted

}
