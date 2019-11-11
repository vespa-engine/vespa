package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toUnmodifiableList;

/**
 * Status of the deployment jobs of an {@link Application}.
 *
 * @author jonmv
 */
public class DeploymentStatus {

    private final Application application;
    private final JobList jobs;

    public DeploymentStatus(Application application, Map<JobId, JobStatus> jobs) {
        this.application = Objects.requireNonNull(application);
        this.jobs = JobList.from(jobs.values());
    }

    public Application application() {
        return application;
    }

    public JobList jobs() {
        return jobs;
    }

    public boolean hasFailures() {
        return ! jobs.failing()
                     .not().withStatus(RunStatus.outOfCapacity)
                     .isEmpty();
    }

    public Map<JobType, JobStatus> instanceJobs(InstanceName instance) {
        return jobs.asList().stream()
                   .filter(job -> job.id().application().equals(application.id().instance(instance)))
                   .collect(Collectors.toUnmodifiableMap(job -> job.id().type(),
                                                         job -> job));
    }

    public Map<ApplicationId, JobList> instanceJobs() {
        return jobs.asList().stream()
                   .collect(groupingBy(job -> job.id().application(),
                                       collectingAndThen(toUnmodifiableList(), JobList::from)));
    }

}
