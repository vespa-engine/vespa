// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.zone.RoutingMethod;
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

    /** Returns whether the container is ready to serve. */
    boolean ready(URI endpointUrl);

    /** Returns whether the test container is ready to serve */
    boolean testerReady(DeploymentId deploymentId);

    /** Returns the IP address of the given host name, if any. */
    Optional<String> resolveHostName(HostName hostname);

    /** Returns the host name of the given CNAME, if any. */
    Optional<HostName> resolveCName(HostName hostName);

    enum Status {

        /** Tests have not yet started. */
        NOT_STARTED,

        /** Tests are running. */
        RUNNING,

        /** Tests failed. */
        FAILURE,

        /** The tester encountered an exception. */
        ERROR,

        /** The tests were successful. */
        SUCCESS

    }


    enum Suite {

        system,

        staging_setup,

        staging,

        production;

        public static Suite of(JobType type, boolean isSetup) {
            if (type == JobType.systemTest) return system;
            if (type == JobType.stagingTest) return isSetup ? staging_setup : staging;
            if (type.isProduction()) return production;
            throw new AssertionError("Unknown JobType '" + type + "'!");
        }

    }

}
