// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.persistence;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Stores a queue for each type of job, and offers jobs from each of these to a periodic
 * polling mechanism which is responsible for triggering the offered jobs in an external build service.
 *
 * @author jvenstad
 */
public class DeploymentQueue {

    private final Controller controller;
    private final CuratorDb curator;

    public DeploymentQueue(Controller controller, CuratorDb curator) {
        this.controller = controller;
        this.curator = curator;
    }

    /**
     * Enqueues the given job type for the given application, with priority based on the other parameters.
     *
     * Priority is computed by comparing first whether the triggering is forced, then whether it is caused
     * by an application change, then whether it is a retry of a job which failed due to the system being out
     * of capacity, and finally when it was enqueued.
     *
     * @param applicationId ID of the application for which to enqueue the job
     * @param jobType JobType to enqueue
     * @param force Whether this job is forced, and should have the highest priority
     * @param applicationVersionUpgrade Whether this job carries an application change, and should have high priority
     * @param retry Whether this is a retry, and should have increased priority
     */
    public void addJob(ApplicationId applicationId, JobType jobType, boolean force, boolean applicationVersionUpgrade, boolean retry) {
        locked(jobType, queue -> {
            if (queue.stream().noneMatch(triggering -> triggering.applicationId().equals(applicationId)))
                queue.add(new Triggering(applicationId, controller.clock().instant(), force, applicationVersionUpgrade, retry));
        });
    }

    /** List all jobs currently enqueued. */
    public List<BuildJob> jobs() {
        ImmutableList.Builder<BuildJob> builder = ImmutableList.builder();
        for (JobType jobType : JobType.values())
            for (Triggering triggering : curator.readJobQueue(jobType))
                toBuildJob(triggering.applicationId(), jobType).ifPresent(builder::add);

        return builder.build();
    }

    /** Remove and return a set of jobs to run. This set will contain only one of each job type for capacity constrained zones. */
    public List<BuildJob> takeJobsToRun() {
        ImmutableList.Builder<BuildJob> builder = ImmutableList.builder();
        for (JobType jobType : JobType.values())
            locked(jobType, queue ->
                    queue.stream()
                            .sorted()
                            .limit(isCapacityConstrained(jobType) ? 1 : Long.MAX_VALUE)
                            .peek(triggering -> toBuildJob(triggering.applicationId(), jobType).ifPresent(builder::add))
                            .forEach(queue::remove));

        return builder.build();
    }

    /** Remove all enqueued jobs for the given application. */
    public void removeJobs(ApplicationId applicationId) {
        for (JobType jobType : JobType.values())
            locked(jobType, queue -> {
                queue.removeIf(triggering -> triggering.applicationId().equals(applicationId));
            });
    }

    /** Lock the job queues and read, modify, and store the queue for the given job type. */
    private void locked(JobType jobType, Consumer<Collection<Triggering>> modifications) {
        try (Lock lock = curator.lockJobQueues()) {
            List<Triggering> queue = new CopyOnWriteArrayList<>(curator.readJobQueue(jobType));
            modifications.accept(queue);
            curator.writeJobQueue(jobType, queue);
        }
    }

    private static boolean isCapacityConstrained(JobType jobType) {
        return jobType == JobType.stagingTest || jobType == JobType.systemTest;
    }

    private Optional<BuildJob> toBuildJob(ApplicationId applicationId, JobType jobType) {
        return controller.applications().get(applicationId)
                .flatMap(application -> application.deploymentJobs().projectId())
                .map(projectId -> new BuildJob(projectId, jobType.jobName()));
    }


    static class Triggering implements Comparable<Triggering> {

        private final ApplicationId applicationId;
        private final Instant at;
        private final boolean forced;
        private final boolean applicationVersionUpgrade;
        private final boolean retry;

        Triggering(ApplicationId applicationId, Instant at, boolean forced, boolean applicationVersionUpgrade, boolean retry) {
            this.applicationId = applicationId;
            this.at = at;
            this.forced = forced;
            this.applicationVersionUpgrade = applicationVersionUpgrade;
            this.retry = retry;
        }

        ApplicationId applicationId() { return applicationId; }
        Instant at() { return at; }
        boolean forced() { return forced; }
        boolean applicationVersionUpgrade() { return applicationVersionUpgrade; }
        boolean retry() { return retry; }


        @Override
        public int compareTo(Triggering o) {
            if (forced && ! o.forced) return -1;
            if (applicationVersionUpgrade && ! o.applicationVersionUpgrade) return -1;
            if (retry && ! o.retry) return -1;
            return at.compareTo(o.at);
        }

    }

}

