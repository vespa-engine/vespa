package com.yahoo.vespa.hosted.controller.deployment;


import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.curator.Lock;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.BuildService;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.maintenance.JobControl;
import com.yahoo.vespa.hosted.controller.maintenance.Maintainer;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Stores and triggers build jobs in an external BuildService.
 *
 * Capacity constrained jobs are added to queues which are polled with a given interval.
 * Other jobs are triggered right away, in the provided BuildService.
 *
 * All triggering (constrained and otherwise) can be turned off in the given JobControl.
 *
 * Each triggering spawns its own thread, as there is no guarantee the BuildService provides a timely response.
 *
 * @author jvenstad
 */
public class PushingBuildSystem extends Maintainer implements BuildSystem {

    private static final Logger log = Logger.getLogger(PushingBuildSystem.class.getName());
    // The number of jobs to offer, on each poll, for zones that have limited capacity
    static final Duration constrainedTriggeringInterval = Duration.ofSeconds(30);
    static final int triggeringRetries = 5;

    private final ExecutorService executors;
    private final BuildService buildService;

    @SuppressWarnings("unused") // Used by DI.
    public PushingBuildSystem(Controller controller, JobControl jobControl, BuildService buildService) {
        super(controller, constrainedTriggeringInterval, jobControl);

        this.buildService = buildService;

        executors = Executors.newFixedThreadPool(20);
    }


    @Override
    public void addJob(ApplicationId application, DeploymentJobs.JobType jobType, boolean first) {
        if ( ! projectId(application).isPresent()) {
            log.warning("Not queuing " + jobType.jobName() + " for " + application.toShortString() +
                        " because project ID is missing");
            return;
        }

        // Store jobs that aren't triggered right away.
        if (isCapacityConstrained(jobType) || ! jobControl().isActive(name())) {
            try (Lock lock = curator().lockJobQueues()) {
                Deque<ApplicationId> queue = curator().readJobQueue(jobType);
                if ( ! queue.contains(application)) {
                    if (first)
                        queue.addFirst(application);
                    else
                        queue.add(application);
                    curator().writeJobQueue(jobType, queue);
                }
                else
                    throw new IllegalStateException("Was ordered to trigger " + jobType + " for " + application + ", but this was already enqueued.");
            }
        }
        else
            new Thread(() -> triggerWithRetries(new BuildService.BuildJob(projectId(application).get(), jobType.jobName()), triggeringRetries)).start();
    }

    @Override
    public List<BuildService.BuildJob> jobs() {
        return getJobs(false);
    }

    @Override
    public List<BuildService.BuildJob> takeJobsToRun() {
        return getJobs(true);
    }


    @Override
    public void removeJobs(ApplicationId application) {
        try (Lock lock = curator().lockJobQueues()) {
            for (DeploymentJobs.JobType jobType : DeploymentJobs.JobType.values()) {
                Deque<ApplicationId> queue = curator().readJobQueue(jobType);
                while (queue.remove(application)) {
                    // keep removing until not found
                }
                curator().writeJobQueue(jobType, queue);
            }
        }
    }

    private void triggerWithRetries(BuildService.BuildJob buildJob, int retries) {
        executors.submit(() -> {
            try {
                for (int i = 0; i < retries; i++)
                    if (buildService.trigger(buildJob))
                        return;

                throw new RuntimeException("Exhausted all " + retries + " retries without success.");
            }
            catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to trigger " + buildJob + "; this is likely a transient error.", e);
            }
        });
    }

    private List<BuildService.BuildJob> getJobs(boolean removeFromQueue) {
        int capacityConstrainedJobsOffered = 0;
        try (Lock lock = curator().lockJobQueues()) { // TODO: Why? Because multi-controller, perhaps?
            List<BuildService.BuildJob> jobsToRun = new ArrayList<>();
            for (DeploymentJobs.JobType jobType : DeploymentJobs.JobType.values()) {
                Deque<ApplicationId> queue = curator().readJobQueue(jobType);
                for (ApplicationId a : queue) {
                    ApplicationId application = removeFromQueue ? queue.poll() : a;

                    Optional<Long> projectId = projectId(application);
                    if (projectId.isPresent()) {
                        jobsToRun.add(new BuildService.BuildJob(projectId.get(), jobType.jobName()));
                    } else {
                    }

                    // Return a limited number of jobs at a time for capacity constrained zones
                    if (removeFromQueue && isCapacityConstrained(jobType) &&
                        ++capacityConstrainedJobsOffered >= 2) {
                        break;
                    }
                }
                if (removeFromQueue)
                    curator().writeJobQueue(jobType, queue);
            }
            return Collections.unmodifiableList(jobsToRun);
        }
    }

    private Optional<Long> projectId(ApplicationId applicationId) {
        return controller().applications().require(applicationId).deploymentJobs().projectId();
    }

    private static boolean isCapacityConstrained(DeploymentJobs.JobType jobType) {
        return jobType == DeploymentJobs.JobType.stagingTest || jobType == DeploymentJobs.JobType.systemTest;
    }

    @Override
    protected void maintain() {
        Set<BuildService.BuildJob> jobsToTrigger = new LinkedHashSet<>();
        // TODO: Store applications with triggering here, instead of in the DeploymentTrigger?
    }

}

