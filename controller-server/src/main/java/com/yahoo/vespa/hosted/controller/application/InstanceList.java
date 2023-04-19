// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.application;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.component.VersionCompatibility;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.versions.VersionStatus;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion;
import com.yahoo.vespa.hosted.controller.versions.VespaVersion.Confidence;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

/**
 * @author jonmv
 */
public class InstanceList extends AbstractFilteringList<ApplicationId, InstanceList> {

    private final Map<ApplicationId, DeploymentStatus> instances;

    private InstanceList(Collection<? extends ApplicationId> items, boolean negate, Map<ApplicationId, DeploymentStatus> instances) {
        super(items, negate, (i, n) -> new InstanceList(i, n, instances));
        this.instances = Map.copyOf(instances);
    }

    /**
     * Returns the subset of instances where all production deployments are compatible with the given version,
     * and at least one known build is compatible with the given version.
     *
     * @param platform the version which applications returned are compatible with
     */
    public InstanceList compatibleWithPlatform(Version platform, Function<ApplicationId, VersionCompatibility> compatibility) {
        return matching(id ->    instance(id).productionDeployments().values().stream()
                                             .flatMap(deployment -> application(id).revisions().get(deployment.revision()).compileVersion().stream())
                                             .noneMatch(version -> compatibility.apply(id).refuse(platform, version))
                              && application(id).revisions().production().stream()
                                                .anyMatch(revision -> revision.compileVersion()
                                                                              .map(compiled -> compatibility.apply(id).accept(platform, compiled))
                                                                              .orElse(true)));
    }

    /**
     * Returns the subset of instances whose application have a deployment on the given major,
     * or specify it in deployment spec,
     * or which are on a {@link VespaVersion.Confidence#legacy} platform, and do not specify that in deployment spec.
     *
     * @param targetMajorVersion the target major version which applications returned allows upgrading to
     */
    public InstanceList allowingMajorVersion(int targetMajorVersion, VersionStatus versions) {
        return matching(id -> {
            Application application = application(id);
            Optional<Integer> majorVersion = application.deploymentSpec().majorVersion();
            if (majorVersion.isPresent())
                return majorVersion.get() >= targetMajorVersion;

            for (List<Deployment> deployments : application.productionDeployments().values())
                for (Deployment deployment : deployments) {
                    if (deployment.version().getMajor() >= targetMajorVersion) return true;
                    if (versions.version(deployment.version()).confidence() == Confidence.legacy) return true;
                }
            return false;
        });
    }

    /** Returns the subset of instances that are allowed to upgrade to the given version at the given time */
    public InstanceList canUpgradeAt(Version version, Instant instant) {
        return matching(id -> instances.get(id).instanceSteps().get(id.instance())
                                       .readiness(Change.of(version)).okAt(instant));
    }

    /** Returns the subset of instances which have at least one production deployment */
    public InstanceList withProductionDeployment() {
        return matching(id -> instance(id).productionDeployments().size() > 0);
    }

    /** Returns the subset of instances which contain declared jobs */
    public InstanceList withDeclaredJobs() {
        return matching(id ->    instances.get(id).application().revisions().last().isPresent()
                              && instances.get(id).jobSteps().values().stream()
                                          .anyMatch(job -> job.isDeclared() && job.job().get().application().equals(id)));
    }

    /** Returns the subset of instances which have at least one deployment on a lower version than the given one, or which have no production deployments */
    public InstanceList onLowerVersionThan(Version version) {
        return matching(id ->    instance(id).productionDeployments().isEmpty()
                              || instance(id).productionDeployments().values().stream()
                                             .anyMatch(deployment -> deployment.version().isBefore(version)));
    }

    /** Returns the subset of instances that has completed deployment of given change */
    public InstanceList hasCompleted(Change change) {
        return matching(id -> instances.get(id).hasCompleted(id.instance(), change));
    }

    /** Returns the subset of instances which are currently deploying a change */
    public InstanceList deploying() {
        return matching(id -> instance(id).change().hasTargets());
    }

    /** Returns the subset of instances which are currently deploying a new revision */
    public InstanceList changingRevision() {
        return matching(id -> instance(id).change().revision().isPresent());
    }

    /** Returns the subset of instances which currently have failing jobs on the given version */
    public InstanceList failingOn(Version version) {
        return matching(id -> ! instances.get(id).instanceJobs().get(id).failingHard()
                                         .lastCompleted().on(version).isEmpty());
    }

    /** Returns the subset of instances which are not pinned to a certain Vespa version. */
    public InstanceList unpinned() {
        return matching(id -> ! instance(id).change().isPlatformPinned());
    }

    /** Returns the subset of instances which are currently failing a job. */
    public InstanceList failing() {
        return matching(id -> ! instances.get(id).instanceJobs().get(id).failingHard().isEmpty());
    }

    /** Returns the subset of instances which are currently failing an upgrade. */
    public InstanceList failingUpgrade() {
        return matching(id -> ! instances.get(id).instanceJobs().get(id).failingHard().not().failingApplicationChange().isEmpty());
    }

    /** Returns the subset of instances which are upgrading (to any version), not considering block windows. */
    public InstanceList upgrading() {
        return matching(id -> instance(id).change().platform().isPresent());
    }

    /** Returns the subset of instances which are currently upgrading to the given version */
    public InstanceList upgradingTo(Version version) {
        return upgradingTo(List.of(version));
    }


    /** Returns the subset of instances which are currently upgrading to the given version */
    public InstanceList upgradingTo(Collection<Version> versions) {
        return matching(id -> versions.stream().anyMatch(version -> instance(id).change().platform().equals(Optional.of(version))));
    }

    public InstanceList with(DeploymentSpec.UpgradePolicy policy) {
        return matching(id -> application(id).deploymentSpec().requireInstance(id.instance()).upgradePolicy() == policy);
    }

    /** Returns the subset of instances which started failing on the given version */
    public InstanceList startedFailingOn(Version version) {
        return matching(id -> ! instances.get(id).instanceJobs().get(id).firstFailing().on(version).isEmpty());
    }

    /** Returns this list sorted by increasing oldest production deployment version. Applications without any deployments are ordered first. */
    public InstanceList byIncreasingDeployedVersion() {
        return sortedBy(comparing(id -> instance(id).productionDeployments().values().stream()
                                                    .map(Deployment::version)
                                                    .min(naturalOrder())
                                                    .orElse(Version.emptyVersion)));
    }

    private Application application(ApplicationId id) {
        return instances.get(id).application();
    }

    private Instance instance(ApplicationId id) {
        return application(id).require(id.instance());
    }

    public static InstanceList from(DeploymentStatusList statuses) {
        Map<ApplicationId, DeploymentStatus> instances = new HashMap<>();
        for (DeploymentStatus status : statuses.asList())
            for (InstanceName instance : status.application().deploymentSpec().instanceNames())
                instances.put(status.application().id().instance(instance), status);
        return new InstanceList(instances.keySet(), false, instances);
    }

}
