// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.util.Deque;
import java.util.List;
import java.util.Optional;
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

    public void addJob(ApplicationId applicationId, JobType jobType, boolean first) {
        locked(jobType, queue -> {
            if (first)
                queue.addFirst(applicationId);
            else
                queue.addLast(applicationId);
        });
    }

    public List<BuildJob> jobs() {
        ImmutableList.Builder<BuildJob> builder = ImmutableList.builder();
        for (JobType jobType : JobType.values())
            locked(jobType, queue ->
                    queue.forEach(id -> toBuildJob(id, jobType).ifPresent(builder::add)));

        return builder.build();
    }

    public List<BuildJob> takeJobsToRun() {
        ImmutableList.Builder<BuildJob> builder = ImmutableList.builder();
        for (JobType jobType : JobType.values())
            locked(jobType, queue ->
                    queue.stream()
                            .limit(isCapacityConstrained(jobType) ? 1 : 1 << 30)
                            .peek(id -> toBuildJob(id, jobType).ifPresent(builder::add))
                            .forEach(queue::remove));

        return builder.build();
    }

    public void removeJobs(ApplicationId applicationId) {
        for (JobType jobType : JobType.values())
            locked(jobType, queue -> {
                while (queue.remove(applicationId)); // Keep removing until not found.
            });
    }

    /** Lock the job queues and read, modify, and store the queue for the given job type. */
    private void locked(JobType jobType, Consumer<Deque<ApplicationId>> modifications) {
        try (Lock lock = curator.lockJobQueues()) {
            Deque<ApplicationId> queue = curator.readJobQueue(jobType);
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

}
