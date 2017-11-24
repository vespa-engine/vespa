// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.google.common.collect.ImmutableList;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService.BuildJob;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Maintainer;

import java.time.Duration;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores and triggers build jobs in an external BuildService.
 *
 * Capacity constrained jobs are added to FIFO queues which are polled with a given interval.
 * Other jobs are triggered right away, with the provided BuildService, unless this maintainer
 * is currently disabled, in which case they, too, are stored in queues.
 *
 * All triggering (constrained and otherwise) can be turned off in the given JobControl.
 *
 * Triggering is performed by an ExecutorService, as there is no guarantee the BuildService provides a timely response.
 *
 * @author jvenstad
 */
public class PushingBuildSystem extends Maintainer implements BuildSystem {

    private static final Logger log = Logger.getLogger(PushingBuildSystem.class.getName());
    static final Duration triggeringInterval = Duration.ofSeconds(30);
    static final int triggeringRetries = 5;

    private final ExecutorService executors;
    private final BuildService buildService;

    @SuppressWarnings("unused") // Used by DI.
    public PushingBuildSystem(Controller controller, JobControl jobControl, BuildService buildService) {
        super(controller, triggeringInterval, jobControl);
        this.buildService = buildService;
        this.executors = Executors.newFixedThreadPool(20);
    }

    @Override
    public void addJob(ApplicationId applicationId, JobType jobType, boolean first) {
        if (isCapacityConstrained(jobType) || ! jobControl().isActive(name()))
            locked(jobType, queue -> {
                if (first)
                    queue.addFirst(applicationId);
                else
                    queue.addLast(applicationId);
            });
        else
            triggerWithRetries(applicationId, jobType);
    }

    @Override
    public List<BuildJob> jobs() {
        ImmutableList.Builder<BuildJob> builder = ImmutableList.builder();
        for (JobType jobType : JobType.values())
            for (ApplicationId applicationId : curator().readJobQueue(jobType))
                projectId(applicationId).ifPresent(projectId -> builder.add(new BuildJob(projectId, jobType.jobName())));

        return builder.build();
    }

    @Override
    public List<BuildJob> takeJobsToRun() {
        throw new UnsupportedOperationException("I'll do that myself, thank you very much!");
    }

    @Override
    public void removeJobs(ApplicationId applicationId) {
        for (JobType jobType : JobType.values())
            locked(jobType, queue -> {
                while (queue.remove(applicationId)); // Keep removing until not found.
            });
    }

    @Override
    protected void maintain() {
        triggerStoredJobs();
    }

    private void triggerStoredJobs() {
        for (JobType jobType : JobType.values())
            locked(jobType, queue ->
                    queue.stream()
                            .limit(isCapacityConstrained(jobType) ? 1 : 10)
                            .peek(applicationId -> triggerWithRetries(applicationId, jobType))
                            .forEach(queue::remove));
    }

    private void triggerWithRetries(ApplicationId applicationId, JobType jobType) {
        projectId(applicationId).ifPresent(projectId -> {
            executors.submit(() -> {
                for (int i = 0; i < triggeringRetries; i++)
                    if (buildService.trigger(new BuildJob(projectId, jobType.jobName())))
                        return;

                log.log(Level.WARNING, "Exhausted all " + triggeringRetries + " retries without success.");
            });
        });
    }

    private Optional<Long> projectId(ApplicationId applicationId) {
        return controller().applications().get(applicationId)
                .flatMap(application -> application.deploymentJobs().projectId());
    }

    private static boolean isCapacityConstrained(JobType jobType) {
        return jobType == JobType.stagingTest || jobType == JobType.systemTest;
    }

    /** Lock the job queues and read, modify, and store the queue for the given job type. */
    private void locked(JobType jobType, Consumer<Deque<ApplicationId>> modifications) {
        try (Lock lock = curator().lockJobQueues()) {
            Deque<ApplicationId> queue = curator().readJobQueue(jobType);
            modifications.accept(queue);
            curator().writeJobQueue(jobType, queue);
        }
    }

}
