// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.zone.ZoneApi;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.maintenance.config.MaintainerConfig;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
    private final HostRepairMaintainer hostRepairMaintainer;


    @Inject
    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(MaintainerConfig maintainerConfig,
                                 Controller controller,
                                 CuratorDb curator,
                                 Metric metric) {
        Duration maintenanceInterval = Duration.ofMinutes(maintainerConfig.intervalMinutes());
        deploymentExpirer = new DeploymentExpirer(controller, maintenanceInterval);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, controller.serviceRegistry().deploymentIssues(), maintenanceInterval);
        metricsReporter = new MetricsReporter(controller, metric);
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, Duration.ofMinutes(3));
        versionStatusUpdater = new VersionStatusUpdater(controller, Duration.ofMinutes(3));
        upgrader = new Upgrader(controller, maintenanceInterval, curator);
        readyJobsTrigger = new ReadyJobsTrigger(controller, Duration.ofMinutes(1));
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, Duration.ofMinutes(5));
        applicationOwnershipConfirmer = new ApplicationOwnershipConfirmer(controller, Duration.ofHours(12), controller.serviceRegistry().ownershipIssues());
        systemUpgrader = new SystemUpgrader(controller, Duration.ofMinutes(1));
        jobRunner = new JobRunner(controller, Duration.ofSeconds(90));
        osUpgraders = osUpgraders(controller);
        osVersionStatusUpdater = new OsVersionStatusUpdater(controller, maintenanceInterval);
        contactInformationMaintainer = new ContactInformationMaintainer(controller, Duration.ofHours(12));
        nameServiceDispatcher = new NameServiceDispatcher(controller, Duration.ofSeconds(10));
        costReportMaintainer = new CostReportMaintainer(controller, Duration.ofHours(2), controller.serviceRegistry().costReportConsumer());
        resourceMeterMaintainer = new ResourceMeterMaintainer(controller, Duration.ofMinutes(1), metric, controller.serviceRegistry().meteringService());
        cloudEventReporter = new CloudEventReporter(controller, Duration.ofMinutes(30), metric);
        rotationStatusUpdater = new RotationStatusUpdater(controller, maintenanceInterval);
        resourceTagMaintainer = new ResourceTagMaintainer(controller, Duration.ofMinutes(30), controller.serviceRegistry().resourceTagger());
        systemRoutingPolicyMaintainer = new SystemRoutingPolicyMaintainer(controller, Duration.ofMinutes(10));
        applicationMetaDataGarbageCollector = new ApplicationMetaDataGarbageCollector(controller, Duration.ofHours(12));
        hostRepairMaintainer = new HostRepairMaintainer(controller, Duration.ofHours(12));
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
        hostRepairMaintainer.close();
    }

    /** Create one OS upgrader per cloud found in the zone registry of controller */
    private static List<OsUpgrader> osUpgraders(Controller controller) {
        return controller.zoneRegistry().zones().controllerUpgraded().zones().stream()
                         .map(ZoneApi::getCloudName)
                         .distinct()
                         .sorted()
                         .map(cloud -> new OsUpgrader(controller, Duration.ofMinutes(1), cloud))
                         .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

}
