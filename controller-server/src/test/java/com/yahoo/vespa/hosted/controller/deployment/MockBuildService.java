// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
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

import static com.yahoo.vespa.hosted.controller.deployment.MockBuildService.JobStatus.QUEUED;
import static com.yahoo.vespa.hosted.controller.deployment.MockBuildService.JobStatus.RUNNING;

/**
 * Simulates polling of build jobs from the controller and triggering and execution of
 * these in Screwdriver.
 *
 * @author jvenstad
 */
public class MockBuildService implements BuildService {

    private final ControllerTester tester;
    private final MockTimeline timeline;
    private final Map<String, Job> jobs;
    private final Map<String, JobStatus> jobStatuses;
    private Version version;

    public MockBuildService(ControllerTester tester, MockTimeline timeline) {
        this.tester = tester;
        this.timeline = timeline;
        jobs = new HashMap<>();
        jobStatuses = new HashMap<>();
        version = new Version(6, 86);
    }

    /** Simulates the triggering of a Screwdriver job, where jobs are queued if already running. */
    @Override
    public boolean trigger(BuildJob buildJob) {
        String key = buildJob.toString();
        System.err.println(timeline.now() + ": Asked to trigger " + key);

        if ( ! jobStatuses.containsKey(key))
            startJob(key);
        else
            jobStatuses.put(key, QUEUED);

        return true;
    }

    /** Simulates the internal triggering of Screwdriver, where only one instance is run at a time. */
    private void startJob(String key) {
        jobStatuses.put(key, RUNNING);
        Job job = jobs.get(key);
        if (job == null)
            return;

        timeline.in(job.duration, () -> {
            job.outcome();
            if (jobStatuses.get(key) == QUEUED)
                startJob(key);
            else
                jobStatuses.remove(key);
        });
        System.err.println(timeline.now() + ": Triggered " + key + "; it will finish at " + timeline.now().plus(job.duration));
    }

    public void incrementVersion() {
        version = new Version(version.getMajor(), version.getMinor() + 1);
    }

    public Version version() { return version; }

    /** Add @job to the set of @Job objects we have information about. */
    private void add(Job job) {
        jobs.put(job.buildJob().toString(), job);
    }

    /** Add @project to the set of @Project objects we have information about. */
    private void add(Project project) {
        project.jobs.values().forEach(this::add);
    }

    /** Make a @Project with the given settings, modify it if desired, and @add() it its jobs to the pool of known ones. */
    public Project project(ApplicationId applicationId, Long projectId, Duration duration, Supplier<Boolean> success) {
        return new Project(applicationId, projectId, duration, success);
    }


    /** Convenience creator for many jobs, belonging to the same project. Jobs can be modified independently after creation. */
    class Project {

        private final ApplicationId applicationId;
        private final Long projectId;
        private final Duration duration;
        private final Supplier<Boolean> success;
        private final Map<JobType, Job> jobs;

        private Project(ApplicationId applicationId, Long projectId, Duration duration, Supplier<Boolean> success) {
            this.applicationId = applicationId;
            this.projectId = projectId;
            this.duration = duration;
            this.success = success;

            jobs = new EnumMap<>(JobType.class);

            for (JobType jobType : JobType.values())
                jobs.put(jobType, new Job(applicationId, projectId, jobType, duration, success));
        }

        /** Set @duration for @jobType of this @Project. */
        public Project set(Duration duration, JobType jobType) {
            jobs.compute(jobType, (type, job) -> new Job(applicationId, projectId, jobType, duration, job.success));
            return this;
        }

        /** Set @success for @jobType of this @Project. */
        public Project set(Supplier<Boolean> success, JobType jobType) {
            jobs.compute(jobType, (type, job) -> new Job(applicationId, projectId, jobType, job.duration, success));
            return this;
        }

        /** Add the @Job objects of this @Project to the pool of known jobs for this @MockBuildService. */
        public void add() {
            MockBuildService.this.add(this);
        }

    }


    /** Representation of a simulated job -- most noteworthy is the @outcome(), which is used to simulate a job completing. */
    private class Job {

        private final ApplicationId applicationId;
        private final Long projectId;
        private final JobType jobType;
        private final Duration duration;
        private final Supplier<Boolean> success;

        private Job(ApplicationId applicationId, Long projectId, JobType jobType, Duration duration, Supplier<Boolean> success) {
            this.applicationId = applicationId;
            this.projectId = projectId;
            this.jobType = jobType;
            this.duration = duration;
            this.success = success;
        }

        private void outcome() {
            Boolean success = this.success.get();
            System.err.println(timeline.now() + ": Job " + projectId + ":" + jobType + " reports " + success);
            if (success != null)
                tester.controller().applications().notifyJobCompletion(
                        new DeploymentJobs.JobReport(
                                applicationId,
                                jobType,
                                projectId,
                                42,
                                Optional.empty(),
                                Optional.ofNullable(success ? null : JobError.unknown)
                        ));
        }

        private BuildJob buildJob() { return new BuildJob(projectId, jobType.jobName()); }

    }

    enum JobStatus {
        QUEUED,
        RUNNING
    }

}
