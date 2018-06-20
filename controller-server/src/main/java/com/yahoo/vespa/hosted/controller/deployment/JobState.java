package com.yahoo.vespa.hosted.controller.deployment;

/**
 * Status of jobs run by an {@link InternalBuildService}.
 *
 * @author jonmv
 */
public enum JobState {

    /** Job is not currently running, and may be started. */
    idle,

    /** Real application is deploying. */
    deploying,

    /** Real application is converging. */
    converging,

    /** Tester is starting up, but is not yet ready to serve its status. */
    initializing,

    /** Job is up and running normally. */
    running,

    /** Tests are complete, and results may be fetched. */
    finished

}
