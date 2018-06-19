package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Outcomes of jobs run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public enum JobOutcome {

    /** Deployment of the real application was rejected due to missing capacity. */
    outOfCapacity,

    /** Deployment of the real application was rejected. */
    deploymentFailed,

    /** Convergence of the real application timed out. */
    convergenceFailed,

    /** Real application was deployed, but the tester application was not. */
    testError,

    /** Real application was deployed, but the tests failed. */
    testFailure,

    /** Deployment and tests completed with great success! */
    success,

    /** Job completed abnormally, due to user intervention or unexpected system error. */
    aborted

}
