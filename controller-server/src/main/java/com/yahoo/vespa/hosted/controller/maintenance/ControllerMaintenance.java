// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.athenz.AthenzClientFactory;
import com.yahoo.vespa.hosted.controller.api.integration.user.UserManagement;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static java.time.temporal.ChronoUnit.HOURS;
import static java.time.temporal.ChronoUnit.MINUTES;
import static java.time.temporal.ChronoUnit.SECONDS;

/**
 * Maintenance jobs of the controller.
 * Each maintenance job is a singleton instance of its implementing class, created and owned by this,
 * and running its own dedicated thread.
 *
 * @author bratseth
 */
public class ControllerMaintenance extends AbstractComponent {

    private final Upgrader upgrader;
    private final OsUpgradeScheduler osUpgradeScheduler;
    private final List<Maintainer> maintainers = new CopyOnWriteArrayList<>();

    @Inject
    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(Controller controller, Metric metric, UserManagement userManagement, AthenzClientFactory athenzClientFactory) {
        Intervals intervals = new Intervals(controller.system());
        SuccessFactorBaseline successFactorBaseline = new SuccessFactorBaseline(controller.system());
        upgrader = new Upgrader(controller, intervals.defaultInterval);
        osUpgradeScheduler = new OsUpgradeScheduler(controller, intervals.osUpgradeScheduler);
        maintainers.add(upgrader);
        maintainers.add(osUpgradeScheduler);
        maintainers.addAll(osUpgraders(controller, intervals.osUpgrader));
        maintainers.add(new DeploymentExpirer(controller, intervals.defaultInterval));
        maintainers.add(new DeploymentInfoMaintainer(controller, intervals.deploymentInfoMaintainer));
        maintainers.add(new DeploymentUpgrader(controller, intervals.defaultInterval));
        maintainers.add(new DeploymentIssueReporter(controller, controller.serviceRegistry().deploymentIssues(), intervals.defaultInterval));
        maintainers.add(new MetricsReporter(controller, metric, athenzClientFactory.createZmsClient()));
        maintainers.add(new OutstandingChangeDeployer(controller, intervals.outstandingChangeDeployer));
        maintainers.add(new VersionStatusUpdater(controller, intervals.versionStatusUpdater));
        maintainers.add(new ReadyJobsTrigger(controller, intervals.readyJobsTrigger));
        maintainers.add(new DeploymentMetricsMaintainer(controller, intervals.deploymentMetricsMaintainer, successFactorBaseline.deploymentMetricsMaintainerBaseline));
        maintainers.add(new ApplicationOwnershipConfirmer(controller, intervals.applicationOwnershipConfirmer, controller.serviceRegistry().ownershipIssues()));
        maintainers.add(new SystemUpgrader(controller, intervals.systemUpgrader));
        maintainers.add(new JobRunner(controller, intervals.jobRunner));
        maintainers.add(new OsVersionStatusUpdater(controller, intervals.osVersionStatusUpdater));
        maintainers.add(new ContactInformationMaintainer(controller, intervals.contactInformationMaintainer));
        maintainers.add(new NameServiceDispatcher(controller, intervals.nameServiceDispatcher));
        maintainers.add(new CostReportMaintainer(controller, intervals.costReportMaintainer, controller.serviceRegistry().costReportConsumer()));
        maintainers.add(new ResourceMeterMaintainer(controller, intervals.resourceMeterMaintainer, metric, controller.serviceRegistry().resourceDatabase()));
        maintainers.add(new ResourceTagMaintainer(controller, intervals.resourceTagMaintainer, controller.serviceRegistry().resourceTagger()));
        maintainers.add(new ApplicationMetaDataGarbageCollector(controller, intervals.applicationMetaDataGarbageCollector));
        maintainers.add(new ArtifactExpirer(controller, intervals.containerImageExpirer));
        maintainers.add(new HostInfoUpdater(controller, intervals.hostInfoUpdater));
        maintainers.add(new ReindexingTriggerer(controller, intervals.reindexingTriggerer));
        maintainers.add(new EndpointCertificateMaintainer(controller, intervals.endpointCertificateMaintainer));
        maintainers.add(new BcpGroupUpdater(controller, intervals.trafficFractionUpdater, successFactorBaseline.trafficFractionUpdater));
        maintainers.add(new ArchiveUriUpdater(controller, intervals.archiveUriUpdater));
        maintainers.add(new ArchiveAccessMaintainer(controller, metric, intervals.archiveAccessMaintainer));
        maintainers.add(new TenantRoleMaintainer(controller, intervals.tenantRoleMaintainer));
        maintainers.add(new TenantRoleCleanupMaintainer(controller, intervals.tenantRoleMaintainer));
        maintainers.add(new ChangeRequestMaintainer(controller, intervals.changeRequestMaintainer));
        maintainers.add(new VcmrMaintainer(controller, intervals.vcmrMaintainer, metric));
        maintainers.add(new CloudDatabaseMaintainer(controller, intervals.defaultInterval));
        maintainers.add(new CloudTrialExpirer(controller, intervals.defaultInterval));
        maintainers.add(new RetriggerMaintainer(controller, intervals.retriggerMaintainer));
        maintainers.add(new UserManagementMaintainer(controller, intervals.userManagementMaintainer, controller.serviceRegistry().roleMaintainer()));
        maintainers.add(new BillingDatabaseMaintainer(controller, intervals.billingDatabaseMaintainer));
        maintainers.add(new MeteringMonitorMaintainer(controller, intervals.meteringMonitorMaintainer, controller.serviceRegistry().resourceDatabase(), metric));
        maintainers.add(new EnclaveAccessMaintainer(controller, intervals.defaultInterval));
    }

    public Upgrader upgrader() { return upgrader; }

    public OsUpgradeScheduler osUpgradeScheduler() { return osUpgradeScheduler; }

    @Override
    public void deconstruct() {
        maintainers.forEach(Maintainer::shutdown);
        maintainers.forEach(Maintainer::awaitShutdown);
    }

    /** Create one OS upgrader per cloud found in the zone registry of controller */
    private static List<OsUpgrader> osUpgraders(Controller controller, Duration interval) {
        return controller.zoneRegistry().zones().controllerUpgraded().zones().stream()
                         .map(ZoneApi::getCloudName)
                         .distinct()
                         .sorted()
                         .map(cloud -> new OsUpgrader(controller, interval, cloud))
                         .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

    private static class Intervals {

        private static final Duration MAX_CD_INTERVAL = Duration.ofHours(1);

        private final SystemName system;

        private final Duration defaultInterval;
        private final Duration deploymentInfoMaintainer;
        private final Duration outstandingChangeDeployer;
        private final Duration versionStatusUpdater;
        private final Duration readyJobsTrigger;
        private final Duration deploymentMetricsMaintainer;
        private final Duration applicationOwnershipConfirmer;
        private final Duration systemUpgrader;
        private final Duration jobRunner;
        private final Duration osVersionStatusUpdater;
        private final Duration osUpgrader;
        private final Duration osUpgradeScheduler;
        private final Duration contactInformationMaintainer;
        private final Duration nameServiceDispatcher;
        private final Duration costReportMaintainer;
        private final Duration resourceMeterMaintainer;
        private final Duration resourceTagMaintainer;
        private final Duration applicationMetaDataGarbageCollector;
        private final Duration containerImageExpirer;
        private final Duration hostInfoUpdater;
        private final Duration reindexingTriggerer;
        private final Duration endpointCertificateMaintainer;
        private final Duration trafficFractionUpdater;
        private final Duration archiveUriUpdater;
        private final Duration archiveAccessMaintainer;
        private final Duration tenantRoleMaintainer;
        private final Duration changeRequestMaintainer;
        private final Duration vcmrMaintainer;
        private final Duration retriggerMaintainer;
        private final Duration userManagementMaintainer;
        private final Duration billingDatabaseMaintainer;
        private final Duration meteringMonitorMaintainer;

        public Intervals(SystemName system) {
            this.system = Objects.requireNonNull(system);
            this.defaultInterval = duration(system.isCd() ? 1 : 5, MINUTES);
            this.deploymentInfoMaintainer = duration(system.isCd() ? 1 : 10, MINUTES);
            this.outstandingChangeDeployer = duration(3, MINUTES);
            this.versionStatusUpdater = duration(3, MINUTES);
            this.readyJobsTrigger = duration(1, MINUTES);
            this.deploymentMetricsMaintainer = duration(10, MINUTES);
            this.applicationOwnershipConfirmer = duration(3, HOURS);
            this.systemUpgrader = duration(2, MINUTES);
            this.jobRunner = duration(system.isCd() ? 45 : 90, SECONDS);
            this.osVersionStatusUpdater = duration(2, MINUTES);
            this.osUpgrader = duration(1, MINUTES);
            this.osUpgradeScheduler = duration(15, MINUTES);
            this.contactInformationMaintainer = duration(12, HOURS);
            this.nameServiceDispatcher = duration(10, SECONDS);
            this.costReportMaintainer = duration(2, HOURS);
            this.resourceMeterMaintainer = duration(3, MINUTES);
            this.resourceTagMaintainer = duration(30, MINUTES);
            this.applicationMetaDataGarbageCollector = duration(12, HOURS);
            this.containerImageExpirer = duration(12, HOURS);
            this.hostInfoUpdater = duration(12, HOURS);
            this.reindexingTriggerer = duration(1, HOURS);
            this.endpointCertificateMaintainer = duration(1, HOURS);
            this.trafficFractionUpdater = duration(5, MINUTES);
            this.archiveUriUpdater = duration(5, MINUTES);
            this.archiveAccessMaintainer = duration(10, MINUTES);
            this.tenantRoleMaintainer = duration(5, MINUTES);
            this.changeRequestMaintainer = duration(1, HOURS);
            this.vcmrMaintainer = duration(1, HOURS);
            this.retriggerMaintainer = duration(1, MINUTES);
            this.userManagementMaintainer = duration(12, HOURS);
            this.billingDatabaseMaintainer = duration(5, MINUTES);
            this.meteringMonitorMaintainer = duration(30, MINUTES);
        }

        private Duration duration(long amount, TemporalUnit unit) {
            Duration duration = Duration.of(amount, unit);
            if (system.isCd() && duration.compareTo(MAX_CD_INTERVAL) > 0) {
                return MAX_CD_INTERVAL; // Ensure that maintainer is given enough time to run in CD
            }
            return duration;
        }

    }

    private static class SuccessFactorBaseline {

        private final Double defaultSuccessFactorBaseline;
        private final Double deploymentMetricsMaintainerBaseline;
        private final Double trafficFractionUpdater;

        public SuccessFactorBaseline(SystemName system) {
            Objects.requireNonNull(system);
            this.defaultSuccessFactorBaseline = 1.0;
            this.deploymentMetricsMaintainerBaseline = 0.95;
            this.trafficFractionUpdater = system.isCd() ? 0.5 : 0.65;
        }
    }
}
