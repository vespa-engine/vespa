package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;

import java.util.List;

/**
 * A singleton owned by the controller, which contains the state and methods for controlling deployment jobs.
 *
 * Keys are the {@link ApplicationId} of the real application, for which the deployment job is run, and the
 * {@link ZoneId} of the real deployment to test.
 *
 * Although the deployment jobs are themselves applications, their IDs are not to be referenced.
 *
 * @author jonmv
 */
public class JobController {

    private final Controller controller;
    private final LogStore logs;

    public JobController(Controller controller) {
        this.controller = controller;
        this.logs = null;
    }


// GET:
    /** Returns whether the given application has registered with this build service. */
    boolean builds(ApplicationId application) {
        return false;
    }

    /** Returns a list of all application which have registered. */
    List<ApplicationId> applications() {
        return null;
    }

    /** Returns all job types which have been run for the given application. */
    List<ZoneId> jobs(ApplicationId application) {
        return null;
    }

    /** Returns a list of meta information about all known runs of the given job type. */
    List<JobMeta> runs(ApplicationId application, ZoneId zone) {
        return null;
    }

    /** Returns the current status of the given job. */
    JobMeta status(JobId job) {
        return null;
    }

    /** Returns the details for the given job. */
    JobDetails details(JobId job) {
        return null;
    }


// POST:
    /** Registers the given application, such that it may have deployment jobs run here. */
    void register(ApplicationId application) {
        ;
    }

    /** Orders a run of the given type, and returns the id of the created job. */
    JobId run(ApplicationId application, ZoneId zone) {
        return null;
    }


// PUT:
    /** Stores the given details for the given job. */
    void store(JobDetails details, JobId job) {
        ;
    }


// DELETE:
    /** Unregisters the given application, and deletes all associated data. */
    void unregister(ApplicationId application) {
        ;
    }

    /** Aborts the given job. */
    void abort(JobId job) {
        ;
    }

}
