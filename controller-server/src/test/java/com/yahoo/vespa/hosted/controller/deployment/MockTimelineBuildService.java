// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.vespa.hosted.controller.ApplicationController;
import com.yahoo.vespa.hosted.controller.ControllerTester;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobError;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static com.yahoo.vespa.hosted.controller.deployment.MockTimelineBuildService.JobRunStatus.ENQUEUED;
import static com.yahoo.vespa.hosted.controller.deployment.MockTimelineBuildService.JobRunStatus.RUNNING;

/**
 * Simulates enqueueing of build jobs in an external build service.
 *
 * The external build service simulated here allows only one concurrent execution of each job,
 * and enqueueing a job which is currently running makes this job run one more time when finished,
 * regardless of the number of times it is enqueued.
 *
 * @author jvenstad
 */
public class MockTimelineBuildService implements BuildService {

    private final ApplicationController applications;
    private final MockTimeline timeline;
    private final Map<String, Job> jobs;
    private final Map<String, JobRunStatus> jobStatuses;

    public MockTimelineBuildService(ControllerTester tester, MockTimeline timeline) {
        this.applications = tester.controller().applications();
        this.timeline = timeline;
        jobs = new HashMap<>();
        jobStatuses = new HashMap<>();
    }

    /** Simulates the triggering of a Screwdriver job, where jobs are enqueued if already running. */
    @Override
    public boolean trigger(BuildJob buildJob) {
        String key = buildJob.jobName() + "@" + buildJob.projectId();
        System.err.println(timeline.now() + ": Asked to trigger " + key);

        if ( ! jobStatuses.containsKey(key))
            start(key);
        else
            jobStatuses.put(key, ENQUEUED);

        return true;
    }

    /** Simulates the internal triggering of Screwdriver, where only one instance is run at a time. */
    private void start(String key) {
        jobStatuses.put(key, RUNNING);
        Job job = jobs.get(key);
        if (job == null)
            return;

        timeline.in(job.duration, () -> {
            job.outcome();
            if (jobStatuses.get(key) == ENQUEUED)
                start(key);
            else
                jobStatuses.remove(key);
        });
        System.err.println(timeline.now() + ": Triggered " + key + "; it will finish at " + timeline.now().plus(job.duration));
    }

    /** Add job to the set of Job objects we have information about. */
    private void add(Job job) {
        jobs.put(job.buildJob().jobName() + "@" + job.buildJob().projectId(), job);
    }

    /** Add project to the set of Project objects we have information about. */
    private void add(Project project) {
        project.jobs.values().forEach(this::add);
    }

    // TODO: Replace with something that relies on ApplicationPackage.
    /** Make a Project with the given settings, modify it if desired, and add() it its jobs to the pool of known ones. */
    public Project project(ApplicationId applicationId, Long projectId, Duration duration, Supplier<JobError> error) {
        return new Project(applicationId, projectId, duration, error);
    }


    /** Convenience creator for many jobs belonging to the same project. Jobs can be modified independently after creation. */
    class Project {

        private final ApplicationId applicationId;
        private final Long projectId;
        private final Map<JobType, Job> jobs;

        private Project(ApplicationId applicationId, Long projectId, Duration duration, Supplier<JobError> error) {
            this.applicationId = applicationId;
            this.projectId = projectId;

            jobs = new EnumMap<>(JobType.class);

            for (JobType jobType : JobType.values())
                jobs.put(jobType, new Job(applicationId, projectId, jobType, duration, error));
        }

        /** Set duration for jobType of this Project. */
        public Project set(Duration duration, JobType jobType) {
            jobs.compute(jobType, (__, job) -> new Job(applicationId, projectId, jobType, duration, job.error));
            return this;
        }

        /** Set success for jobType of this Project. */
        public Project set(Supplier<JobError> error, JobType jobType) {
            jobs.compute(jobType, (__, job) -> new Job(applicationId, projectId, jobType, job.duration, error));
            return this;
        }

        /** Add the Job objects of this Project to the pool of known jobs for this MockBuildService. */
        public void add() {
            MockTimelineBuildService.this.add(this);
        }

    }


    /** Representation of a simulated job -- most noteworthy is the outcome(), which is used to simulate a job completing. */
    private class Job {

        private final ApplicationId applicationId;
        private final Long projectId;
        private final JobType jobType;
        private final Duration duration;
        private final Supplier<JobError> error;

        private long buildNumber = 0;

        private Job(ApplicationId applicationId, Long projectId, JobType jobType, Duration duration, Supplier<JobError> error) {
            this.applicationId = applicationId;
            this.projectId = projectId;
            this.jobType = jobType;
            this.duration = duration;
            this.error = error;
        }

        private void outcome() {
            if (error == null) return; // null JobError supplier means the job doesn't report back, i.e., is aborted.

            JobError jobError = this.error.get();
            System.err.println(timeline.now() + ": Job " + projectId + ":" + jobType + " reports " + (jobError == null ? " success " : jobError));
            applications.notifyJobCompletion(new DeploymentJobs.JobReport(applicationId,
                                                                          jobType,
                                                                          projectId,
                                                                          ++buildNumber, // TODO: Increase this on triggering instead.
                                                                          Optional.ofNullable(jobError)));
        }

        private BuildJob buildJob() { return new BuildJob(projectId, jobType.jobName()); }

    }

    enum JobRunStatus {
        ENQUEUED,
        RUNNING
    }

}
