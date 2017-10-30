// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller;

import com.google.common.collect.ImmutableMap;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.application.api.ValidationOverrides;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.hosted.controller.application.Change;
import com.yahoo.vespa.hosted.controller.application.Deployment;
import com.yahoo.vespa.hosted.controller.application.DeploymentJobs;

import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * An instance of an application.
 * 
 * This is immutable.
 * 
 * @author bratseth
 */
public class Application {

    private final ApplicationId id;
    private final DeploymentSpec deploymentSpec;
    private final ValidationOverrides validationOverrides;
    private final Map<Zone, Deployment> deployments;
    private final DeploymentJobs deploymentJobs;
    private final Optional<Change> deploying;
    private final boolean outstandingChange;

    /** Creates an empty application */
    public Application(ApplicationId id) {
        this(id, DeploymentSpec.empty, ValidationOverrides.empty, ImmutableMap.of(),
             new DeploymentJobs(Optional.empty(), Collections.emptyList(), Optional.empty()),
             Optional.empty(), false);
    }

    /** Used from persistence layer: Do not use */
    public Application(ApplicationId id, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides, 
                       List<Deployment> deployments, 
                       DeploymentJobs deploymentJobs, Optional<Change> deploying, boolean outstandingChange) {
        this(id, deploymentSpec, validationOverrides, 
             deployments.stream().collect(Collectors.toMap(Deployment::zone, d -> d)),
             deploymentJobs, deploying, outstandingChange);
    }

    Application(ApplicationId id, DeploymentSpec deploymentSpec, ValidationOverrides validationOverrides,
                Map<Zone, Deployment> deployments, DeploymentJobs deploymentJobs, Optional<Change> deploying,
                boolean outstandingChange) {
        Objects.requireNonNull(id, "id cannot be null");
        Objects.requireNonNull(deploymentSpec, "deploymentSpec cannot be null");
        Objects.requireNonNull(validationOverrides, "validationOverrides cannot be null");
        Objects.requireNonNull(deployments, "deployments cannot be null");
        Objects.requireNonNull(deploymentJobs, "deploymentJobs cannot be null");
        Objects.requireNonNull(deploying, "deploying cannot be null");
        this.id = id;
        this.deploymentSpec = deploymentSpec;
        this.validationOverrides = validationOverrides;
        this.deployments = ImmutableMap.copyOf(deployments);
        this.deploymentJobs = deploymentJobs;
        this.deploying = deploying;
        this.outstandingChange = outstandingChange;
    }

    public ApplicationId id() { return id; }
    
    /** 
     * Returns the last deployed deployment spec of this application, 
     * or the empty deployment spec if it has never been deployed 
     */
    public DeploymentSpec deploymentSpec() { return deploymentSpec; }

    /**
     * Returns the last deployed validation overrides of this application, 
     * or the empty validation overrides if it has never been deployed
     * (or was deployed with an empty/missing validation overrides)
     */
    public ValidationOverrides validationOverrides() { return validationOverrides; }
    
    /** Returns an immutable map of the current deployments of this */
    public Map<Zone, Deployment> deployments() { return deployments; }

    /** 
     * Returns an immutable map of the current *production* deployments of this
     * (deployments also includes manually deployed environments)
     */
    public Map<Zone, Deployment> productionDeployments() {
        return deployments.values().stream()
                                   .filter(deployment -> deployment.zone().environment() == Environment.prod)
                                   .collect(Collectors.toMap(Deployment::zone, Function.identity()));
    }

    public DeploymentJobs deploymentJobs() { return deploymentJobs; }

    /**
     * Returns the change that is currently in the process of being deployed on this application, 
     * or empty if no change is currently being deployed.
     */
    public Optional<Change> deploying() { return deploying; }

    /**
     * Returns whether this has an outstanding change (in the source repository), which
     * has currently not started deploying (because a deployment is (or was) already in progress
     */
    public boolean hasOutstandingChange() { return outstandingChange; }

    /** 
     * Returns the oldest version this has deployed in a permanent zone (not test or staging),
     * or empty version if it is not deployed anywhere
     */
    public Optional<Version> deployedVersion() {
        return productionDeployments().values().stream()
                                               .sorted(Comparator.comparing(Deployment::version))
                                               .findFirst()
                                               .map(Deployment::version);
    }

    /** The version that should be used to compile this application */
    public Version compileVersion(Controller controller) {
        return deployedVersion().orElse(controller.systemVersion());
    }

    /** Returns the version a deployment to this zone should use for this application */
    public Version currentDeployVersion(Controller controller, Zone zone) {
        if ( ! deploying().isPresent())
            return currentVersion(controller, zone);
        else if ( deploying().get() instanceof Change.ApplicationChange)
            return currentVersion(controller, zone);
        else
            return ((Change.VersionChange) deploying().get()).version();
    }

    /** Returns the current version this application has, or if none; should use, in the given zone */
    public Version currentVersion(Controller controller, Zone zone) {
        Deployment currentDeployment = deployments().get(zone);
        if (currentDeployment != null) // Already deployed in this zone: Use that version
            return currentDeployment.version();

        return deployedVersion().orElse(controller.systemVersion());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (! (o instanceof Application)) return false;

        Application that = (Application) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }

    @Override
    public String toString() {
        return "application '" + id + "'";
    }

    /** Returns true if there is no current change to deploy - i.e deploying is empty or completely deployed */
    public boolean deployingCompleted() { 
        if ( ! deploying.isPresent()) return true;
        return deploymentJobs().isDeployed(deploying.get()); 
    }

    /** Returns true if there is a current change which is blocked from being deployed to production at this instant */
    public boolean deployingBlocked(Instant instant) {
        if ( ! deploying.isPresent()) return false;
        return deploying.get().blockedBy(deploymentSpec, instant);
    }
    
    public boolean isBlocked(Instant instant) {
        return ! deploymentSpec.canUpgradeAt(instant) || ! deploymentSpec.canChangeRevisionAt(instant);
    }
    
}
