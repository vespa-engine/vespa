// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.SystemName;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;

import java.time.Duration;
import java.time.temporal.TemporalUnit;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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

    private final DeploymentExpirer deploymentExpirer;
    private final DeploymentIssueReporter deploymentIssueReporter;
    private final MetricsReporter metricsReporter;
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final VersionStatusUpdater versionStatusUpdater;
    private final Upgrader upgrader;
    private final ReadyJobsTrigger readyJobsTrigger;
    private final DeploymentMetricsMaintainer deploymentMetricsMaintainer;
    private final ApplicationOwnershipConfirmer applicationOwnershipConfirmer;
    private final SystemUpgrader systemUpgrader;
    private final List<OsUpgrader> osUpgraders;
    private final OsVersionStatusUpdater osVersionStatusUpdater;
    private final JobRunner jobRunner;
    private final ContactInformationMaintainer contactInformationMaintainer;
    private final CostReportMaintainer costReportMaintainer;
    private final ResourceMeterMaintainer resourceMeterMaintainer;
    private final NameServiceDispatcher nameServiceDispatcher;
    private final CloudEventReporter cloudEventReporter;
    private final RotationStatusUpdater rotationStatusUpdater;
    private final ResourceTagMaintainer resourceTagMaintainer;
    private final SystemRoutingPolicyMaintainer systemRoutingPolicyMaintainer;
    private final ApplicationMetaDataGarbageCollector applicationMetaDataGarbageCollector;
    private final ContainerImageExpirer containerImageExpirer;
    private final HostSwitchUpdater hostSwitchUpdater;

    @Inject
    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(Controller controller, Metric metric) {
        Intervals intervals = new Intervals(controller.system());
        deploymentExpirer = new DeploymentExpirer(controller, intervals.defaultInterval);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, controller.serviceRegistry().deploymentIssues(), intervals.defaultInterval);
        metricsReporter = new MetricsReporter(controller, metric);
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, intervals.outstandingChangeDeployer);
        versionStatusUpdater = new VersionStatusUpdater(controller, intervals.versionStatusUpdater);
        upgrader = new Upgrader(controller, intervals.defaultInterval);
        readyJobsTrigger = new ReadyJobsTrigger(controller, intervals.readyJobsTrigger);
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, intervals.deploymentMetricsMaintainer);
        applicationOwnershipConfirmer = new ApplicationOwnershipConfirmer(controller, intervals.applicationOwnershipConfirmer, controller.serviceRegistry().ownershipIssues());
        systemUpgrader = new SystemUpgrader(controller, intervals.systemUpgrader);
        jobRunner = new JobRunner(controller, intervals.jobRunner);
        osUpgraders = osUpgraders(controller, intervals.osUpgrader);
        osVersionStatusUpdater = new OsVersionStatusUpdater(controller, intervals.defaultInterval);
        contactInformationMaintainer = new ContactInformationMaintainer(controller, intervals.contactInformationMaintainer);
        nameServiceDispatcher = new NameServiceDispatcher(controller, intervals.nameServiceDispatcher);
        costReportMaintainer = new CostReportMaintainer(controller, intervals.costReportMaintainer, controller.serviceRegistry().costReportConsumer());
        resourceMeterMaintainer = new ResourceMeterMaintainer(controller, intervals.resourceMeterMaintainer, metric, controller.serviceRegistry().meteringService());
        cloudEventReporter = new CloudEventReporter(controller, intervals.cloudEventReporter, metric);
        rotationStatusUpdater = new RotationStatusUpdater(controller, intervals.defaultInterval);
        resourceTagMaintainer = new ResourceTagMaintainer(controller, intervals.resourceTagMaintainer, controller.serviceRegistry().resourceTagger());
        systemRoutingPolicyMaintainer = new SystemRoutingPolicyMaintainer(controller, intervals.systemRoutingPolicyMaintainer);
        applicationMetaDataGarbageCollector = new ApplicationMetaDataGarbageCollector(controller, intervals.applicationMetaDataGarbageCollector);
        containerImageExpirer = new ContainerImageExpirer(controller, intervals.containerImageExpirer);
        hostSwitchUpdater = new HostSwitchUpdater(controller, intervals.hostSwitchUpdater);
    }

    public Upgrader upgrader() { return upgrader; }

    @Override
    public void deconstruct() {
        deploymentExpirer.close();
        deploymentIssueReporter.close();
        metricsReporter.close();
        outstandingChangeDeployer.close();
        versionStatusUpdater.close();
        upgrader.close();
        readyJobsTrigger.close();
        deploymentMetricsMaintainer.close();
        applicationOwnershipConfirmer.close();
        systemUpgrader.close();
        osUpgraders.forEach(ControllerMaintainer::close);
        osVersionStatusUpdater.close();
        jobRunner.close();
        contactInformationMaintainer.close();
        costReportMaintainer.close();
        resourceMeterMaintainer.close();
        nameServiceDispatcher.close();
        cloudEventReporter.close();
        rotationStatusUpdater.close();
        resourceTagMaintainer.close();
        systemRoutingPolicyMaintainer.close();
        applicationMetaDataGarbageCollector.close();
        containerImageExpirer.close();
        hostSwitchUpdater.close();
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
        private final Duration osUpgrader;
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

        public Intervals(SystemName system) {
            this.system = Objects.requireNonNull(system);
            this.defaultInterval = duration(system.isCd() || system == SystemName.dev ? 1 : 5, MINUTES);
            this.outstandingChangeDeployer = duration(3, MINUTES);
            this.versionStatusUpdater = duration(3, MINUTES);
            this.readyJobsTrigger = duration(1, MINUTES);
            this.deploymentMetricsMaintainer = duration(5, MINUTES);
            this.applicationOwnershipConfirmer = duration(12, HOURS);
            this.systemUpgrader = duration(1, MINUTES);
            this.jobRunner = duration(90, SECONDS);
            this.osUpgrader = duration(1, MINUTES);
            this.contactInformationMaintainer = duration(12, HOURS);
            this.nameServiceDispatcher = duration(10, SECONDS);
            this.costReportMaintainer = duration(2, HOURS);
            this.resourceMeterMaintainer = duration(1, MINUTES);
            this.cloudEventReporter = duration(30, MINUTES);
            this.resourceTagMaintainer = duration(30, MINUTES);
            this.systemRoutingPolicyMaintainer = duration(10, MINUTES);
            this.applicationMetaDataGarbageCollector = duration(12, HOURS);
            this.containerImageExpirer = duration(2, HOURS);
            this.hostSwitchUpdater = duration(12, HOURS);
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
