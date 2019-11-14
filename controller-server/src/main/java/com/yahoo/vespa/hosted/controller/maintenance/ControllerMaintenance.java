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

    private final JobControl jobControl;

    private final DeploymentExpirer deploymentExpirer;
    private final DeploymentIssueReporter deploymentIssueReporter;
    private final MetricsReporter metricsReporter;
    private final OutstandingChangeDeployer outstandingChangeDeployer;
    private final VersionStatusUpdater versionStatusUpdater;
    private final Upgrader upgrader;
    private final ReadyJobsTrigger readyJobsTrigger;
    private final ClusterInfoMaintainer clusterInfoMaintainer;
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
    private final BillingMaintainer billingMaintainer;
    private final CloudEventReporter cloudEventReporter;
    private final RotationStatusUpdater rotationStatusUpdater;
    private final ResourceTagMaintainer resourceTagMaintainer;

    @Inject
    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(MaintainerConfig maintainerConfig,
                                 Controller controller,
                                 CuratorDb curator,
                                 JobControl jobControl,
                                 Metric metric) {
        Duration maintenanceInterval = Duration.ofMinutes(maintainerConfig.intervalMinutes());
        this.jobControl = jobControl;
        deploymentExpirer = new DeploymentExpirer(controller, maintenanceInterval, jobControl);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, controller.serviceRegistry().deploymentIssues(), maintenanceInterval, jobControl);
        metricsReporter = new MetricsReporter(controller, metric, jobControl);
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, Duration.ofMinutes(1), jobControl);
        versionStatusUpdater = new VersionStatusUpdater(controller, Duration.ofMinutes(1), jobControl);
        upgrader = new Upgrader(controller, maintenanceInterval, jobControl, curator);
        readyJobsTrigger = new ReadyJobsTrigger(controller, Duration.ofMinutes(1), jobControl);
        clusterInfoMaintainer = new ClusterInfoMaintainer(controller, Duration.ofHours(2), jobControl);
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, Duration.ofMinutes(5), jobControl);
        applicationOwnershipConfirmer = new ApplicationOwnershipConfirmer(controller, Duration.ofHours(12), jobControl, controller.serviceRegistry().ownershipIssues());
        systemUpgrader = new SystemUpgrader(controller, Duration.ofMinutes(1), jobControl);
        jobRunner = new JobRunner(controller, Duration.ofSeconds(90), jobControl);
        osUpgraders = osUpgraders(controller, jobControl);
        osVersionStatusUpdater = new OsVersionStatusUpdater(controller, maintenanceInterval, jobControl);
        contactInformationMaintainer = new ContactInformationMaintainer(controller, Duration.ofHours(12), jobControl);
        nameServiceDispatcher = new NameServiceDispatcher(controller, Duration.ofSeconds(10), jobControl);
        costReportMaintainer = new CostReportMaintainer(controller, Duration.ofHours(2), jobControl, controller.serviceRegistry().costReportConsumer());
        resourceMeterMaintainer = new ResourceMeterMaintainer(controller, Duration.ofMinutes(30), jobControl, metric, controller.serviceRegistry().meteringService());
        billingMaintainer = new BillingMaintainer(controller, Duration.ofDays(3), jobControl);
        cloudEventReporter = new CloudEventReporter(controller, Duration.ofDays(1), jobControl);
        rotationStatusUpdater = new RotationStatusUpdater(controller, maintenanceInterval, jobControl);
        resourceTagMaintainer = new ResourceTagMaintainer(controller, Duration.ofMinutes(30), jobControl, controller.serviceRegistry().resourceTagger());
    }

    public Upgrader upgrader() { return upgrader; }
    
    /** Returns control of the maintenance jobs of this */
    public JobControl jobControl() { return jobControl; }

    @Override
    public void deconstruct() {
        deploymentExpirer.deconstruct();
        deploymentIssueReporter.deconstruct();
        metricsReporter.deconstruct();
        outstandingChangeDeployer.deconstruct();
        versionStatusUpdater.deconstruct();
        upgrader.deconstruct();
        readyJobsTrigger.deconstruct();
        clusterInfoMaintainer.deconstruct();
        deploymentMetricsMaintainer.deconstruct();
        applicationOwnershipConfirmer.deconstruct();
        systemUpgrader.deconstruct();
        osUpgraders.forEach(Maintainer::deconstruct);
        osVersionStatusUpdater.deconstruct();
        jobRunner.deconstruct();
        contactInformationMaintainer.deconstruct();
        costReportMaintainer.deconstruct();
        resourceMeterMaintainer.deconstruct();
        nameServiceDispatcher.deconstruct();
        billingMaintainer.deconstruct();
        cloudEventReporter.deconstruct();
        rotationStatusUpdater.deconstruct();
        resourceTagMaintainer.deconstruct();
    }

    /** Create one OS upgrader per cloud found in the zone registry of controller */
    private static List<OsUpgrader> osUpgraders(Controller controller, JobControl jobControl) {
        return controller.zoneRegistry().zones().controllerUpgraded().zones().stream()
                         .map(ZoneApi::getCloudName)
                         .distinct()
                         .sorted()
                         .map(cloud -> new OsUpgrader(controller, Duration.ofMinutes(1), jobControl, cloud))
                         .collect(Collectors.collectingAndThen(Collectors.toList(), Collections::unmodifiableList));
    }

}
