package com.yahoo.vespa.hosted.controller.application;

import com.google.common.collect.ImmutableMap;
import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeploymentSpec;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.vespa.hosted.controller.Application;
import com.yahoo.vespa.hosted.controller.Instance;
import com.yahoo.vespa.hosted.controller.api.integration.deployment.ApplicationVersion;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatus;
import com.yahoo.vespa.hosted.controller.deployment.DeploymentStatusList;
import com.yahoo.vespa.hosted.controller.deployment.RunStatus;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.util.Comparator.comparing;
import static java.util.Comparator.naturalOrder;

public class InstanceList extends AbstractFilteringList<ApplicationId, InstanceList> {

    private final Map<ApplicationId, DeploymentStatus> statuses;

    private InstanceList(Collection<? extends ApplicationId> items, boolean negate, Map<ApplicationId, DeploymentStatus> statuses) {
        super(items, negate, (i, n) -> new InstanceList(i, n, statuses));
        this.statuses = statuses;
    }

    public static InstanceList from(DeploymentStatusList statuses) {
        ImmutableMap.Builder<ApplicationId, DeploymentStatus> builder = ImmutableMap.builder();
        for (DeploymentStatus status : statuses.asList())
            for (InstanceName instance : status.application().deploymentSpec().instanceNames())
                builder.put(status.application().id().instance(instance), status);
        Map<ApplicationId, DeploymentStatus> map = builder.build();
        return new InstanceList(map.keySet(), false, map);
    }

    /**
     * Returns the subset of instances that aren't pinned to an an earlier major version than the given one.
     *
     * @param targetMajorVersion the target major version which applications returned allows upgrading to
     * @param defaultMajorVersion the default major version to assume for applications not specifying one
     */
    public InstanceList allowMajorVersion(int targetMajorVersion, int defaultMajorVersion) {
        return matching(id -> targetMajorVersion <= application(id).deploymentSpec().majorVersion()
                                                                   .orElse(application(id).majorVersion()
                                                                                          .orElse(defaultMajorVersion)));
    }

    /** Returns the subset of instances that are allowed to upgrade to the given version at the given time */
    public InstanceList canUpgradeAt(Version version, Instant instant) {
        return matching(id -> statuses.get(id).instanceSteps().get(id.instance())
                                      .readyAt(Change.of(version))
                                      .map(readyAt -> ! readyAt.isAfter(instant)).orElse(false));
    }

    /** Returns the subset of instances which have at least one productiog deployment */
    public InstanceList withProductionDeployment() {
        return matching(id -> instance(id).productionDeployments().size() > 0);
    }

    /** Returns the subset of instances which have at least one deployment on a lower version than the given one */
    public InstanceList onLowerVersionThan(Version version) {
        return matching(id -> instance(id).productionDeployments().values().stream()
                                          .anyMatch(deployment -> deployment.version().isBefore(version)));
    }

    /** Returns the subset of instances which have changes left to deploy; blocked, or deploying */
    public InstanceList withChanges() {
        return matching(id -> instance(id).change().hasTargets() || statuses.get(id).outstandingChange(id.instance()).hasTargets());
    }

    /** Returns the subset of instances which are currently deploying a change */
    public InstanceList deploying() {
        return matching(id -> instance(id).change().hasTargets());
    }

    /** Returns the subset of instances which currently have failing jobs on the given version */
    public InstanceList failingOn(Version version) {
        return matching(id -> ! statuses.get(id).instanceJobs().get(id).failing().lastCompleted().on(version).isEmpty());
    }

    /** Returns the subset of instances which are not pinned to a certain Vespa version. */
    public InstanceList unpinned() {
        return matching(id -> ! instance(id).change().isPinned());
    }

    /** Returns the subset of instances which are not currently failing any jobs. */
    public InstanceList failing() {
        return matching(id -> ! statuses.get(id).instanceJobs().get(id).failing().not().withStatus(RunStatus.outOfCapacity).isEmpty());
    }

    /** Returns the subset of instances which are currently failing an upgrade. */
    public InstanceList failingUpgrade() {
        return matching(id -> ! statuses.get(id).instanceJobs().get(id).failing().not().failingApplicationChange().isEmpty());
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
        return matching(id -> ! statuses.get(id).instanceJobs().get(id).firstFailing().on(version).isEmpty());
    }

    /** Returns this list sorted by increasing oldest production deployment version. Applications without any deployments are ordered first. */
    public InstanceList byIncreasingDeployedVersion() {
        return sortedBy(comparing(id -> instance(id).productionDeployments().values().stream()
                                                    .map(Deployment::version)
                                                    .min(naturalOrder())
                                                    .orElse(Version.emptyVersion)));
    }

    private Application application(ApplicationId id) {
        return statuses.get(id).application();
    }

    private Instance instance(ApplicationId id) {
        return application(id).require(id.instance());
    }

}
