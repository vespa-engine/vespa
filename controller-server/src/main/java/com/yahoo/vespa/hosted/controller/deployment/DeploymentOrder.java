package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;
import com.yahoo.vespa.hosted.controller.application.JobStatus;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * This class determines order of deployments according to an application's deployment spec
 *
 * @author mpolden
 */
public class DeploymentOrder {

    private static final Logger log = Logger.getLogger(DeploymentOrder.class.getName());

    private final Controller controller;
    private final Clock clock;

    public DeploymentOrder(Controller controller) {
        this.controller = controller;
        this.clock = controller.clock();
    }

    /** Returns the next job(s) to trigger after the given job, or empty if none should be triggered */
    public List<DeploymentJobs.JobType> nextAfter(DeploymentJobs.JobType jobType, Application application) {
        // Always trigger system test after component as deployment spec might not be available yet (e.g. if this is a
        // new application with no previous deployments)
        if (jobType == DeploymentJobs.JobType.component) {
            return Collections.singletonList(DeploymentJobs.JobType.systemTest);
        }

        // At this point we've at least deployed to system test, so deployment spec should be available
        List<DeploymentSpec.DeclaredZone> zones = application.deploymentSpec().zones();
        Optional<DeploymentSpec.DeclaredZone> zoneForJob = zoneForJob(application, jobType);
        if (!zoneForJob.isPresent()) {
            return Collections.emptyList();
        }
        int zoneIndex = application.deploymentSpec().zones().indexOf(zoneForJob.get());

        // This is last zone
        if (zoneIndex == zones.size() - 1) {
            return Collections.emptyList();
        }

        // Skip next job if delay has not passed yet
        Duration delay = delayAfter(application, zoneForJob.get());
        Optional<Instant> lastSuccess = Optional.ofNullable(application.deploymentJobs().jobStatus().get(jobType))
                .flatMap(JobStatus::lastSuccess)
                .map(JobStatus.JobRun::at);
        if (lastSuccess.isPresent() && lastSuccess.get().plus(delay).isAfter(clock.instant())) {
            log.info(String.format("Delaying next job after %s of %s by %s", jobType, application, delay));
            return Collections.emptyList();
        }

        DeploymentSpec.DeclaredZone nextZone = application.deploymentSpec().zones().get(zoneIndex + 1);
        return Collections.singletonList(
                DeploymentJobs.JobType.from(controller.system(), nextZone.environment(), nextZone.region().orElse(null))
        );
    }

    private Duration delayAfter(Application application, DeploymentSpec.DeclaredZone zone) {
        int stepIndex = application.deploymentSpec().steps().indexOf(zone);
        if (stepIndex == -1 || stepIndex == application.deploymentSpec().steps().size() - 1) {
            return Duration.ZERO;
        }
        Duration totalDelay = Duration.ZERO;
        List<DeploymentSpec.Step> remainingSteps = application.deploymentSpec().steps()
                .subList(stepIndex + 1, application.deploymentSpec().steps().size());
        for (DeploymentSpec.Step step : remainingSteps) {
            if (!(step instanceof DeploymentSpec.Delay)) {
                break;
            }
            totalDelay = totalDelay.plus(((DeploymentSpec.Delay) step).duration());
        }
        return totalDelay;
    }

    private Optional<DeploymentSpec.DeclaredZone> zoneForJob(Application application, DeploymentJobs.JobType jobType) {
        return application.deploymentSpec()
                .zones()
                .stream()
                .filter(zone -> zone.deploysTo(
                        jobType.environment(),
                        jobType.isProduction() ? jobType.region(controller.system()) : Optional.empty()))
                .findFirst();
    }

}
