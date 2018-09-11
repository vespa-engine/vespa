package com.yahoo.vespa.hosted.controller.api.integration.deployment;

import com.yahoo.vespa.hosted.controller.api.integration.LogEntry;

import java.net.URI;
import java.util.List;

/**
 * Allows running some predefined tests -- typically remotely.
 *
 * @author jonmv
 */
public interface TesterCloud {

    /** Signals the tester to run its tests. */
    void startTests(URI testerUrl, Suite suite, byte[] config);

    /** Returns the log entries from the tester with ids after the given threshold. */
    List<LogEntry> getLog(URI testerUrl, long after);

    /** Returns the current status of the tester. */
    Status getStatus(URI testerUrl);

    /** Returns whether the tester is ready to serve. */
    boolean ready(URI testerUrl);


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
