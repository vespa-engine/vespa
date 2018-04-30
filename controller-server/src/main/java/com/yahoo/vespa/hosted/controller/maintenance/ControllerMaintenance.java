// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.controller.Controller;
import com.yahoo.vespa.hosted.controller.api.integration.dns.NameService;
import com.yahoo.vespa.hosted.controller.api.integration.noderepository.NodeRepositoryClientInterface;
import com.yahoo.vespa.hosted.controller.api.integration.organization.OwnershipIssues;
import com.yahoo.vespa.hosted.controller.api.integration.organization.DeploymentIssues;
import com.yahoo.vespa.hosted.controller.api.integration.chef.Chef;
import com.yahoo.vespa.hosted.controller.maintenance.config.MaintainerConfig;
import com.yahoo.vespa.hosted.controller.persistence.CuratorDb;

import java.time.Duration;

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
    private final ClusterUtilizationMaintainer clusterUtilizationMaintainer;
    private final DeploymentMetricsMaintainer deploymentMetricsMaintainer;
    private final ApplicationOwnershipConfirmer applicationOwnershipConfirmer;
    private final DnsMaintainer dnsMaintainer;
    private final SystemUpgrader systemUpgrader;

    @SuppressWarnings("unused") // instantiated by Dependency Injection
    public ControllerMaintenance(MaintainerConfig maintainerConfig, Controller controller, CuratorDb curator,
                                 JobControl jobControl, Metric metric, Chef chefClient,
                                 DeploymentIssues deploymentIssues, OwnershipIssues ownershipIssues,
                                 NameService nameService, NodeRepositoryClientInterface nodeRepositoryClient) {
        Duration maintenanceInterval = Duration.ofMinutes(maintainerConfig.intervalMinutes());
        this.jobControl = jobControl;
        deploymentExpirer = new DeploymentExpirer(controller, maintenanceInterval, jobControl);
        deploymentIssueReporter = new DeploymentIssueReporter(controller, deploymentIssues, maintenanceInterval, jobControl);
        metricsReporter = new MetricsReporter(controller, metric, chefClient, jobControl, controller.system());
        outstandingChangeDeployer = new OutstandingChangeDeployer(controller, maintenanceInterval, jobControl);
        versionStatusUpdater = new VersionStatusUpdater(controller, Duration.ofMinutes(1), jobControl);
        upgrader = new Upgrader(controller, maintenanceInterval, jobControl, curator);
        readyJobsTrigger = new ReadyJobsTrigger(controller, Duration.ofSeconds(30), jobControl);
        clusterInfoMaintainer = new ClusterInfoMaintainer(controller, Duration.ofHours(2), jobControl, nodeRepositoryClient);
        clusterUtilizationMaintainer = new ClusterUtilizationMaintainer(controller, Duration.ofHours(2), jobControl);
        deploymentMetricsMaintainer = new DeploymentMetricsMaintainer(controller, Duration.ofMinutes(10), jobControl);
        applicationOwnershipConfirmer = new ApplicationOwnershipConfirmer(controller, Duration.ofHours(12), jobControl, ownershipIssues);
        dnsMaintainer = new DnsMaintainer(controller, Duration.ofHours(12), jobControl, nameService);
        systemUpgrader = new SystemUpgrader(controller, maintenanceInterval, jobControl);
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
        clusterUtilizationMaintainer.deconstruct();
        clusterInfoMaintainer.deconstruct();
        deploymentMetricsMaintainer.deconstruct();
        applicationOwnershipConfirmer.deconstruct();
        dnsMaintainer.deconstruct();
        systemUpgrader.maintain();
    }

}
