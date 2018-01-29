// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.LockedApplication;
import com.yahoo.vespa.hosted.controller.api.integration.zone.ZoneId;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs.JobType;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.logging.Logger;

import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toList;

/**
 * This class determines the order of deployments according to an application's deployment spec.
 *
 * @author mpolden
 */
public class DeploymentOrder {

    private static final Logger log = Logger.getLogger(DeploymentOrder.class.getName());

    private final Controller controller;
    private final Clock clock;

    public DeploymentOrder(Controller controller) {
        Objects.requireNonNull(controller, "controller cannot be null");
        this.controller = controller;
        this.clock = controller.clock();
    }

    /** Returns a list of jobs to trigger after the given job */
    // TODO: This does too much - should just tell us the order, as advertised
    public List<JobType> nextAfter(JobType job, LockedApplication application) {
        if ( ! application.change().isPresent()) { // Change was cancelled
            return Collections.emptyList();
        }

        // Always trigger system test after component as deployment spec might not be available yet
        // (e.g. if this is a new application with no previous deployments)
        if (job == JobType.component) {
            return Collections.singletonList(JobType.systemTest);
        }

        // At this point we have deployed to system test, so deployment spec is available
        List<DeploymentSpec.Step> deploymentSteps = deploymentSteps(application);
        Optional<DeploymentSpec.Step> currentStep = fromJob(job, application);
        if ( ! currentStep.isPresent()) {
            return Collections.emptyList();
        }

        // If this is the last deployment step there's nothing more to trigger
        int currentIndex = deploymentSteps.indexOf(currentStep.get());
        if (currentIndex == deploymentSteps.size() - 1) {
            return Collections.emptyList();
        }

        // Postpone next job if delay has not passed yet
        Duration delay = delayAfter(currentStep.get(), application);
        if (shouldPostponeDeployment(delay, job, application)) {
            log.info(String.format("Delaying next job after %s of %s by %s", job, application, delay));
            return Collections.emptyList();
        }

        DeploymentSpec.Step nextStep = deploymentSteps.get(currentIndex + 1);
        return nextStep.zones().stream()
                .map(this::toJob)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns jobs for given deployment spec, in the order they are declared */
    public List<JobType> jobsFrom(DeploymentSpec deploymentSpec) {
        return deploymentSpec.steps().stream()
                .flatMap(step -> jobsFrom(step).stream())
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns job status sorted according to deployment spec */
    public List<JobStatus> sortBy(DeploymentSpec deploymentSpec, Collection<JobStatus> jobStatus) {
        List<DeploymentJobs.JobType> sortedJobs = jobsFrom(deploymentSpec);
        return jobStatus.stream()
                .sorted(comparingInt(job -> sortedJobs.indexOf(job.type())))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns deployments sorted according to declared zones */
    public List<Deployment> sortBy(List<DeploymentSpec.DeclaredZone> zones, Collection<Deployment> deployments) {
        List<ZoneId> productionZones = zones.stream()
                .filter(z -> z.region().isPresent())
                .map(z -> ZoneId.from(z.environment(), z.region().get()))
                .collect(toList());
        return deployments.stream()
                .sorted(comparingInt(deployment -> productionZones.indexOf(deployment.zone())))
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Returns jobs for the given step */
    private List<JobType> jobsFrom(DeploymentSpec.Step step) {
        return step.zones().stream()
                .map(this::toJob)
                .collect(collectingAndThen(toList(), Collections::unmodifiableList));
    }

    /** Resolve deployment step from job */
    private Optional<DeploymentSpec.Step> fromJob(JobType job, Application application) {
        for (DeploymentSpec.Step step : application.deploymentSpec().steps()) {
            if (step.deploysTo(job.environment(), job.isProduction() ? job.region(controller.system()) : Optional.empty())) {
                return Optional.of(step);
            }
        }
        return Optional.empty();
    }

    /** Resolve job from deployment step */
    private JobType toJob(DeploymentSpec.DeclaredZone zone) {
        return JobType.from(controller.system(), zone.environment(), zone.region().orElse(null))
                .orElseThrow(() -> new IllegalArgumentException("Invalid zone " + zone));
    }

    /** Returns whether deployment should be postponed according to delay */
    private boolean shouldPostponeDeployment(Duration delay, JobType job, Application application) {
        Optional<Instant> lastSuccess = Optional.ofNullable(application.deploymentJobs().jobStatus().get(job))
                .flatMap(JobStatus::lastSuccess)
                .map(JobStatus.JobRun::at);
        return lastSuccess.isPresent() && lastSuccess.get().plus(delay).isAfter(clock.instant());
    }

    /** Find all steps that deploy to one or more zones */
    private static List<DeploymentSpec.Step> deploymentSteps(Application application) {
        return application.deploymentSpec().steps().stream()
                .filter(step -> ! step.zones().isEmpty())
                .collect(toList());
    }

    /** Determines the delay that should pass after the given step */
    private static Duration delayAfter(DeploymentSpec.Step step, Application application) {
        int stepIndex = application.deploymentSpec().steps().indexOf(step);
        if (stepIndex == -1 || stepIndex == application.deploymentSpec().steps().size() - 1) {
            return Duration.ZERO;
        }
        Duration totalDelay = Duration.ZERO;
        List<DeploymentSpec.Step> remainingSteps = application.deploymentSpec().steps()
                .subList(stepIndex + 1, application.deploymentSpec().steps().size());
        for (DeploymentSpec.Step s : remainingSteps) {
            if (! (s instanceof DeploymentSpec.Delay)) {
                break;
            }
            totalDelay = totalDelay.plus(((DeploymentSpec.Delay) s).duration());
        }
        return totalDelay;
    }

}
