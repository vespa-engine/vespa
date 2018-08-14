package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Status of jobs run by a {@link JobController}.
 *
 * @author jonmv
 */
public enum RunStatus {

    /** Run is still proceeding normally, i.e., without failures. */
    running,

    /** Deployment was rejected due to missing capacity. */
    outOfCapacity,

    /** Deployment of the real application was rejected. */
    deploymentFailed,

    /** Installation of the real application timed out. */
    installationFailed,

    /** The verification tests failed. */
    testFailure,

    /** An unexpected error occurred. */
    error,

    /** Everything completed with great success! */
    success,

    /** Run has been abandoned, due to user intervention or timeout. */
    aborted

}
