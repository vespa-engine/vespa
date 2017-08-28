// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Attempts redeployment of failed jobs and deployments.
 * 
 * @author bratseth
 */
public class FailureRedeployer extends Maintainer {

    private final static Duration jobTimeout = Duration.ofHours(12);
    
    public FailureRedeployer(Controller controller, Duration interval, JobControl jobControl) {
        super(controller, interval, jobControl);
    }

    @Override
    public void maintain() {
        List<Application> applications = controller().applications().asList();
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
            Optional<Map.Entry<JobType, JobStatus>> failingJob = jobFailingFor(application);
            failingJob.ifPresent(job -> triggerFailing(application, "Job " + job.getKey().id() +
                    " has been failing since " + job.getValue().lastCompleted().get()));
        }
    }

    private void retryStuckJobs(List<Application> applications) {
        Instant maxAge = controller().clock().instant().minus(jobTimeout);
        for (Application application : applications) {
            if (!application.deploying().isPresent()) {
                continue;
            }
            Optional<Map.Entry<JobType, JobStatus>> job = oldestRunningJob(application);
            if (job.isPresent() && job.get().getValue().lastTriggered().get().at().isBefore(maxAge)) {
                triggerFailing(application, "Job " + job.get().getKey().id() +
                        " has been running for more than " + jobTimeout);
            }
        }
    }

    private Optional<Map.Entry<JobType, JobStatus>> jobFailingFor(Application application) {
        return application.deploymentJobs().jobStatus().entrySet().stream()
                .filter(e -> !e.getValue().isSuccess() && e.getValue().lastCompletedFor(application.deploying().get()))
                .findFirst();
    }

    private Optional<Map.Entry<JobType, JobStatus>> oldestRunningJob(Application application) {
        return application.deploymentJobs().jobStatus().entrySet().stream()
                .filter(kv -> kv.getValue().inProgress())
                .sorted(Comparator.comparing(kv -> kv.getValue().lastTriggered().get().at()))
                .findFirst();
    }

    private void triggerFailing(Application application, String cause) {
        controller().applications().deploymentTrigger().triggerFailing(application.id(), cause);
    }

}
