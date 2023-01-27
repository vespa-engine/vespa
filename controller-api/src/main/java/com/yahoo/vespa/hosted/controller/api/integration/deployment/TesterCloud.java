// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.EndpointsChecker.Endpoint;
import com.yahoo.config.provision.EndpointsChecker.Availability;
import com.yahoo.vespa.hosted.controller.api.identifiers.DeploymentId;
import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;

import java.net.URI;
import java.util.List;
import java.util.Optional;

/**
 * Allows running some predefined tests -- typically remotely.
 *
 * @author jonmv
 */
public interface TesterCloud {

    /** Signals the tester to run its tests. */
    void startTests(DeploymentId deploymentId, Suite suite, byte[] config);

    /** Returns the log entries from the tester with ids after the given threshold. */
    List<LogEntry> getLog(DeploymentId deploymentId, long after);

    /** Returns the current status of the tester. */
    Status getStatus(DeploymentId deploymentId);

    /** Returns whether the test container is ready to serve */
    boolean testerReady(DeploymentId deploymentId);

    Availability verifyEndpoints(DeploymentId deploymentId, List<Endpoint> endpoints);

    /** Returns the test report as JSON if available */
    Optional<TestReport> getTestReport(DeploymentId deploymentId);

    enum Status {

        /** Tests have not yet started. */
        NOT_STARTED,

        /** Tests are running. */
        RUNNING,

        /** Tests failed. */
        FAILURE,

        /** Tests were inconclusive, and need to run again later. */
        INCONCLUSIVE,

        /** The tester encountered an exception. */
        ERROR,

        /** No tests were found. */
        NO_TESTS,

        /** The tests were successful. */
        SUCCESS

    }


    enum Suite {

        system,

        staging_setup,

        staging,

        production;

        public static Suite of(JobType type, boolean isSetup) {
            if (type.isSystemTest()) return system;
            if (type.isStagingTest()) return isSetup ? staging_setup : staging;
            if (type.isProduction()) return production;
            throw new AssertionError("Unknown JobType '" + type + "'!");
        }

    }

}
