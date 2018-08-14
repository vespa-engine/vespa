package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Outcomes of jobs run by a {@link JobController}.
 *
 * @author jonmv
 */
public enum RunStatus {

    /** Run is still active. */
    running,

    /** Deployment was rejected due to missing capacity. */
    outOfCapacity,

    /** Deployment of the real application was rejected. */
    deploymentFailed,

    /** Installation of the real application timed out. */
    installationFailed,

    /** Installation or initialization of the tester application failed. */
    testError,

    /** The verification tests failed. */
    testFailure,

    /** Everything completed with great success! */
    success,

    /** Run was abandoned, due to user intervention or timeout. */
    aborted

}
