// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Status of jobs run by a {@link JobController}.
 *
 * @author jonmv
 */
public enum RunStatus {

    /** Run is still proceeding normally, i.e., without failures. */
    running,

    /** Deployment was rejected due node allocation failure. */
    nodeAllocationFailure,

    /** Deployment of the real application was rejected because the package is faulty. */
    invalidApplication,

    /** Deployment of the real application was rejected, for other reasons. */
    deploymentFailed,

    /** Deployment timed out waiting for endpoint certificate */
    endpointCertificateTimeout,

    /** Installation of the real application timed out. */
    installationFailed,

    /** The verification tests failed. */
    testFailure,

    /** No tests, for a test job. */
    noTests,

    /** An unexpected error occurred. */
    error,

    /** Everything completed with great success! */
    success,

    /** Run was abandoned, due to user intervention or job timeout. */
    aborted,

    /** Run should be reset to its starting state. Used for production tests. */
    reset

}
