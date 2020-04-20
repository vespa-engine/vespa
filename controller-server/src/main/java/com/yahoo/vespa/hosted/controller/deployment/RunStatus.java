// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Status of jobs run by a {@link JobController}.
 *
 * @author jonmv
 */
public enum RunStatus {

    /** Run is still proceeding normally, i.e., without failures. */
    running,

    /** Deployment was rejected due to lack of capacity. */
    outOfCapacity,

    /** Deployment of the real application was rejected. */
    deploymentFailed,

    /** Deployment timed out waiting for endpoint certificate */
    endpointCertificateTimeout,

    /** Installation of the real application timed out. */
    installationFailed,

    /** The verification tests failed. */
    testFailure,

    /** An unexpected error occurred. */
    error,

    /** Everything completed with great success! */
    success,

    /** Run was abandoned, due to user intervention or job timeout. */
    aborted

}
