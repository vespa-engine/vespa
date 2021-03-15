// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;

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
    private final List<Maintainer> maintainers = new CopyOnWriteArrayList<>();

    @Inject
    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(Controller controller, Metric metric) {
        Intervals intervals = new Intervals(controller.system());
        upgrader = new Upgrader(controller, intervals.defaultInterval);
        maintainers.add(upgrader);
        maintainers.addAll(osUpgraders(controller, intervals.osUpgrader));
        maintainers.add(new DeploymentExpirer(controller, intervals.defaultInterval));
        maintainers.add(new DeploymentIssueReporter(controller, controller.serviceRegistry().deploymentIssues(), intervals.defaultInterval));
        maintainers.add(new MetricsReporter(controller, metric));
        maintainers.add(new OutstandingChangeDeployer(controller, intervals.outstandingChangeDeployer));
        maintainers.add(new VersionStatusUpdater(controller, intervals.versionStatusUpdater));
        maintainers.add(new ReadyJobsTrigger(controller, intervals.readyJobsTrigger));
        maintainers.add(new DeploymentMetricsMaintainer(controller, intervals.deploymentMetricsMaintainer));
        maintainers.add(new ApplicationOwnershipConfirmer(controller, intervals.applicationOwnershipConfirmer, controller.serviceRegistry().ownershipIssues()));
        maintainers.add(new SystemUpgrader(controller, intervals.systemUpgrader));
        maintainers.add(new JobRunner(controller, intervals.jobRunner));
        maintainers.add(new OsVersionStatusUpdater(controller, intervals.osVersionStatusUpdater));
        maintainers.add(new OsUpgradeScheduler(controller, intervals.osUpgradeScheduler));
        maintainers.add(new ContactInformationMaintainer(controller, intervals.contactInformationMaintainer));
        maintainers.add(new NameServiceDispatcher(controller, intervals.nameServiceDispatcher));
        maintainers.add(new CostReportMaintainer(controller, intervals.costReportMaintainer, controller.serviceRegistry().costReportConsumer()));
        maintainers.add(new ResourceMeterMaintainer(controller, intervals.resourceMeterMaintainer, metric, controller.serviceRegistry().meteringService()));
        maintainers.add(new CloudEventReporter(controller, intervals.cloudEventReporter, metric));
        maintainers.add(new ResourceTagMaintainer(controller, intervals.resourceTagMaintainer, controller.serviceRegistry().resourceTagger()));
        maintainers.add(new SystemRoutingPolicyMaintainer(controller, intervals.systemRoutingPolicyMaintainer));
        maintainers.add(new ApplicationMetaDataGarbageCollector(controller, intervals.applicationMetaDataGarbageCollector));
        maintainers.add(new ContainerImageExpirer(controller, intervals.containerImageExpirer));
        maintainers.add(new HostSwitchUpdater(controller, intervals.hostSwitchUpdater));
        maintainers.add(new ReindexingTriggerer(controller, intervals.reindexingTriggerer));
        maintainers.add(new EndpointCertificateMaintainer(controller, intervals.endpointCertificateMaintainer));
        maintainers.add(new TrafficShareUpdater(controller, intervals.trafficFractionUpdater));
        maintainers.add(new ArchiveUriUpdater(controller, intervals.archiveUriUpdater));
        maintainers.add(new TenantRoleMaintainer(controller, intervals.tenantRoleMaintainer));
        maintainers.add(new ChangeRequestMaintainer(controller, intervals.changeRequestMaintainer));
    }

    public Upgrader upgrader() { return upgrader; }

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
        private final Duration cloudEventReporter;
        private final Duration resourceTagMaintainer;
        private final Duration systemRoutingPolicyMaintainer;
        private final Duration applicationMetaDataGarbageCollector;
        private final Duration containerImageExpirer;
        private final Duration hostSwitchUpdater;
        private final Duration reindexingTriggerer;
        private final Duration endpointCertificateMaintainer;
        private final Duration trafficFractionUpdater;
        private final Duration archiveUriUpdater;
        private final Duration tenantRoleMaintainer;
        private final Duration changeRequestMaintainer;

        public Intervals(SystemName system) {
            this.system = Objects.requireNonNull(system);
            this.defaultInterval = duration(system.isCd() || system == SystemName.dev ? 1 : 5, MINUTES);
            this.outstandingChangeDeployer = duration(3, MINUTES);
            this.versionStatusUpdater = duration(3, MINUTES);
            this.readyJobsTrigger = duration(1, MINUTES);
            this.deploymentMetricsMaintainer = duration(10, MINUTES);
            this.applicationOwnershipConfirmer = duration(12, HOURS);
            this.systemUpgrader = duration(2, MINUTES);
            this.jobRunner = duration(90, SECONDS);
            this.osVersionStatusUpdater = duration(2, MINUTES);
            this.osUpgrader = duration(1, MINUTES);
            this.osUpgradeScheduler = duration(3, HOURS);
            this.contactInformationMaintainer = duration(12, HOURS);
            this.nameServiceDispatcher = duration(10, SECONDS);
            this.costReportMaintainer = duration(2, HOURS);
            this.resourceMeterMaintainer = duration(3, MINUTES);
            this.cloudEventReporter = duration(30, MINUTES);
            this.resourceTagMaintainer = duration(30, MINUTES);
            this.systemRoutingPolicyMaintainer = duration(10, MINUTES);
            this.applicationMetaDataGarbageCollector = duration(12, HOURS);
            this.containerImageExpirer = duration(2, HOURS);
            this.hostSwitchUpdater = duration(12, HOURS);
            this.reindexingTriggerer = duration(1, HOURS);
            this.endpointCertificateMaintainer = duration(12, HOURS);
            this.trafficFractionUpdater = duration(5, MINUTES);
            this.archiveUriUpdater = duration(5, MINUTES);
            this.tenantRoleMaintainer = duration(5, MINUTES);
            this.changeRequestMaintainer = duration(12, HOURS);
        }

        private Duration duration(long amount, TemporalUnit unit) {
            Duration duration = Duration.of(amount, unit);
            if (system.isCd() && duration.compareTo(MAX_CD_INTERVAL) > 0) {
                return MAX_CD_INTERVAL; // Ensure that maintainer is given enough time to run in CD
            }
            return duration;
        }

    }

}
