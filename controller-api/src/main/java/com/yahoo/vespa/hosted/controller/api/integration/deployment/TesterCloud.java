package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import java.net.URI;

/**
 * Allows running some predefined tests -- typically remotely.
 *
 * @author jonmv
 */
public interface TesterCloud {

    /** Signals the tester to run its tests. */
    void startTests(URI testerUrl, Suite suite, byte[] config);

    /** Returns the currently stored logs from the tester. */
    byte[] getLogs(URI testerUrl);

    /** Returns the current status of the tester. */
    Status getStatus(URI testerUrl);


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

        staging,

        production;

        public static Suite of(JobType type) {
            if (type == JobType.systemTest) return system;
            if (type == JobType.stagingTest) return  staging;
            if (type.isProduction()) return production;
            throw new AssertionError("Unknown JobType '" + type + "'!");
        }

    }

}
