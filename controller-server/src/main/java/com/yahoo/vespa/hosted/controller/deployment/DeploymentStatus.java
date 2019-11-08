package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobId;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.JobType;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Status of the deployment jobs of an {@link Application}.
 *
 * @author jonmv
 */
public class DeploymentStatus {

    private final Application application;
    private final Map<JobId, JobStatus> jobs;

    public DeploymentStatus(Application application, Map<JobId, JobStatus> jobs) {
        this.application = Objects.requireNonNull(application);
        this.jobs = Map.copyOf(jobs);
    }

    public Application application() {
        return application;
    }

    public Map<JobId, JobStatus> jobs() {
        return jobs;
    }

    public boolean hasFailures() {
        return ! JobList.from(jobs.values())
                        .failing()
                        .not().withStatus(RunStatus.outOfCapacity)
                        .isEmpty();
    }

    public Map<JobType, JobStatus> instanceJobs(InstanceName instance) {
        return jobs.entrySet().stream()
                .filter(entry -> entry.getKey().application().equals(application.id().instance(instance)))
                .collect(Collectors.toUnmodifiableMap(entry -> entry.getKey().type(),
                                                      entry -> entry.getValue()));
    }

}
