// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.ApplicationList;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Attempts redeployment of failed jobs and deployments.
 * 
 * @author bratseth
 * @author mpolden
 */
public class FailureRedeployer extends Maintainer {

    private final static Duration jobTimeout = Duration.ofHours(12);
    
    public FailureRedeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    public void maintain() {
        List<Application> applications = ApplicationList.from(controller().applications().asList())
                .notPullRequest()
                .asList();
        retryFailingJobs(applications);
        retryStuckJobs(applications);
    }

    private void retryFailingJobs(List<Application> applications) {
        for (Application application : applications) {
            if (!application.deploying().isPresent()) {
                continue;
            }
            if (application.deploymentJobs().inProgress()) {
                continue;
            }
            Optional<JobStatus> failingJob = jobFailingFor(application);
            failingJob.ifPresent(job -> triggerFailing(application, "Job " + job.type().id() +
                    " has been failing since " + job.firstFailing().get()));
        }
    }

    private void retryStuckJobs(List<Application> applications) {
        Instant startOfGracePeriod = controller().clock().instant().minus(jobTimeout);
        for (Application application : applications) {
            Optional<JobStatus> job = oldestRunningJob(application);
            if (!job.isPresent()) {
                continue;
            }
            // Ignore job if it doesn't belong to a zone in this system
            if (!job.get().type().zone(controller().system()).isPresent()) {
                continue;
            }
            if (job.get().lastTriggered().get().at().isBefore(startOfGracePeriod)) {
                triggerFailing(application, "Job " + job.get().type().id() +
                        " has been running for more than " + jobTimeout);
            }
        }
    }

    private Optional<JobStatus> jobFailingFor(Application application) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(status -> !status.isSuccess() && status.lastCompletedFor(application.deploying().get()))
                .findFirst();
    }

    private Optional<JobStatus> oldestRunningJob(Application application) {
        return application.deploymentJobs().jobStatus().values().stream()
                .filter(JobStatus::inProgress)
                .sorted(Comparator.comparing(status -> status.lastTriggered().get().at()))
                .findFirst();
    }

    private void triggerFailing(Application application, String cause) {
        controller().applications().deploymentTrigger().triggerFailing(application.id(), cause);
    }

}
