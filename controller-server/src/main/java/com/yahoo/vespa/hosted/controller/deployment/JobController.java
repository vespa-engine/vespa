package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.LogStore;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;
import com.yahoo.vespa.hosted.controller.application.ApplicationVersion;

import java.util.List;

/**
 * A singleton owned by the controller, which contains the state and methods for controlling deployment jobs.
 *
 * Keys are the {@link ApplicationId} of the real application, for which the deployment job is run, and the
 * {@link JobType} of the real deployment to test.
 *
 * Although the deployment jobs are themselves applications, their IDs are not to be referenced.
 *
 * @author jonmv
 */
public class JobController {

    private final Controller controller;
    private final LogStore logs;

    public JobController(Controller controller, LogStore logStore) {
        this.controller = controller;
        this.logs = logStore;
    }

    /** Returns whether the given application has registered with this build service. */
    public boolean builds(ApplicationId id) {
        return controller.applications().get(id)
                         .map(application -> application.deploymentJobs().builtInternally())
                         .orElse(false);
    }

    /** Returns a list of all application which have registered. */
    public List<ApplicationId> applications() {
        return null;
    }

    /** Returns all job types which have been run for the given application. */
    public List<JobType> jobs(ApplicationId id) {
        return null;
    }

    /** Returns a list of meta information about all known runs of the given job type. */
    public List<RunStatus> runs(ApplicationId id, JobType type) {
        return null;
    }

    /** Returns the current status of the given job. */
    public RunStatus status(RunId id) {
        return null;
    }

    /** Returns the details for the given job. */
    public RunDetails details(RunId id) {
        return null;
    }

    /** Registers the given application, such that it may have deployment jobs run here. */
    void register(ApplicationId id) {
        controller.applications().lockIfPresent(id, application ->
                controller.applications().store(application.withBuiltInternally(true)));
    }

    /** Accepts and stores a new appliaction package and test jar pair, and returns the reference these will have. */
    public ApplicationVersion submit(byte[] applicationPackage, byte[] applicationTestJar) {

        // TODO jvenstad: Return versions with increasing numbers.

        return ApplicationVersion.unknown;
    }

    /** Orders a run of the given type, and returns the id of the created job. */
    public RunId run(ApplicationId id, JobType type) {
        return null;
    }

    /** Unregisters the given application, and deletes all associated data. */
    public void unregister(ApplicationId id) {
        controller.applications().lockIfPresent(id, application -> {

            // TODO jvenstad: Clean out data for jobs.

            controller.applications().store(application.withBuiltInternally(false));
        });
    }

    /** Aborts the given job. */
    public void abort(RunId id) {
        ;
    }

}
