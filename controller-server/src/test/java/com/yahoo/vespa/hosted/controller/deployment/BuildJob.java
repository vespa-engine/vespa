// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.ArtifactRepositoryMock;
import com.yahoo.vespa.hosted.controller.application.ApplicationPackage;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.SourceRevision;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Create a build job for testing purposes. In most cases this should be constructed by calling
 * DeploymentTester.jobCompletion.
 *
 * @author mpolden
 */
public class BuildJob {

    public static final SourceRevision defaultSourceRevision = new SourceRevision("repository1",
                                                                                  "master", "commit1");
    public static final long defaultBuildNumber = 42;

    private DeploymentJobs.JobType job;
    private ApplicationId applicationId;
    private Optional<DeploymentJobs.JobError> jobError = Optional.empty();
    private Optional<SourceRevision> sourceRevision = Optional.of(defaultSourceRevision);
    private long projectId;
    private long buildNumber = defaultBuildNumber;

    private final Consumer<DeploymentJobs.JobReport> reportConsumer;
    private final ArtifactRepositoryMock artifactRepository;

    public BuildJob(Consumer<DeploymentJobs.JobReport> reportConsumer, ArtifactRepositoryMock artifactRepository) {
        Objects.requireNonNull(reportConsumer, "reportConsumer cannot be null");
        Objects.requireNonNull(artifactRepository, "artifactRepository cannot be null");
        this.reportConsumer = reportConsumer;
        this.artifactRepository = artifactRepository;
    }

    public BuildJob type(DeploymentJobs.JobType job) {
        this.job = job;
        return this;
    }

    public BuildJob application(Application application) {
        this.applicationId = application.id();
        if (application.deploymentJobs().projectId().isPresent()) {
            this.projectId = application.deploymentJobs().projectId().get();
        }
        return this;
    }

    public BuildJob application(ApplicationId applicationId) {
        this.applicationId = applicationId;
        return this;
    }

    public BuildJob error(DeploymentJobs.JobError jobError) {
        this.jobError = Optional.of(jobError);
        return this;
    }

    public BuildJob sourceRevision(SourceRevision sourceRevision) {
        this.sourceRevision = Optional.of(sourceRevision);
        return this;
    }

    public BuildJob buildNumber(long buildNumber) {
        this.buildNumber = buildNumber;
        return this;
    }

    public BuildJob nextBuildNumber(int increment) {
        return buildNumber(buildNumber + increment);
    }

    public BuildJob nextBuildNumber() {
        return nextBuildNumber(1);
    }

    public BuildJob projectId(long projectId) {
        this.projectId = projectId;
        return this;
    }

    public BuildJob success(boolean success) {
        this.jobError = success ? Optional.empty() : Optional.of(DeploymentJobs.JobError.unknown);
        return this;
    }

    public BuildJob unsuccessful() {
        return success(false);
    }

    /** Create a job report for this build job */
    public DeploymentJobs.JobReport report() {
        return new DeploymentJobs.JobReport(applicationId, job, projectId, buildNumber, sourceRevision, jobError);
    }

    /** Upload given application package to artifact repository as part of this job */
    public BuildJob uploadArtifact(ApplicationPackage applicationPackage) {
        Objects.requireNonNull(job, "job cannot be null");
        Objects.requireNonNull(applicationId, "applicationId cannot be null");
        if (job != DeploymentJobs.JobType.component) {
            throw new IllegalStateException(job + " cannot upload artifact");
        }
        artifactRepository.put(applicationId, applicationPackage, applicationVersion());
        return this;
    }

    /** Send report for this build job to the controller */
    public void submit() {
        if (job == DeploymentJobs.JobType.component &&
            !artifactRepository.contains(applicationId, applicationVersion())) {
            throw new IllegalStateException(job + " must upload artifact before reporting completion");
        }
        reportConsumer.accept(report());
    }

    private String applicationVersion() {
        return String.format("1.0.%d-%s", buildNumber, sourceRevision.get().commit());
    }

}
