// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetrics;
import com.yahoo.vespa.hosted.provision.autoscale.NodeMetricsDb;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.logging.Logger;

/**
 * A component which sets up all the node repo maintenance jobs.
 *
 * @author bratseth
 */
public class NodeRepositoryMaintenance extends AbstractComponent {

    private static final Logger log = Logger.getLogger(NodeRepositoryMaintenance.class.getName());
    private static final String envPrefix = "vespa_node_repository__";

    private final NodeFailer nodeFailer;
    private final PeriodicApplicationMaintainer periodicApplicationMaintainer;
    private final OperatorChangeApplicationMaintainer operatorChangeApplicationMaintainer;
    private final ReservationExpirer reservationExpirer;
    private final InactiveExpirer inactiveExpirer;
    private final RetiredExpirer retiredExpirer;
    private final FailedExpirer failedExpirer;
    private final DirtyExpirer dirtyExpirer;
    private final ProvisionedExpirer provisionedExpirer;
    private final NodeRebooter nodeRebooter;
    private final MetricsReporter metricsReporter;
    private final InfrastructureProvisioner infrastructureProvisioner;
    private final Optional<LoadBalancerExpirer> loadBalancerExpirer;
    private final Optional<DynamicProvisioningMaintainer> dynamicProvisioningMaintainer;
    private final CapacityReportMaintainer capacityReportMaintainer;
    private final OsUpgradeActivator osUpgradeActivator;
    private final Rebalancer rebalancer;
    private final NodeMetricsDbMaintainer nodeMetricsDbMaintainer;
    private final AutoscalingMaintainer autoscalingMaintainer;

    @SuppressWarnings("unused")
    @Inject
    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, InfraDeployer infraDeployer,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor,
                                     Zone zone, Orchestrator orchestrator, Metric metric,
                                     ProvisionServiceProvider provisionServiceProvider, FlagSource flagSource,
                                     NodeMetrics nodeMetrics, NodeMetricsDb nodeMetricsDb) {
        this(nodeRepository, deployer, infraDeployer, hostLivenessTracker, serviceMonitor, zone, Clock.systemUTC(),
             orchestrator, metric, provisionServiceProvider, flagSource, nodeMetrics, nodeMetricsDb);
    }

    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, InfraDeployer infraDeployer,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor,
                                     Zone zone, Clock clock, Orchestrator orchestrator, Metric metric,
                                     ProvisionServiceProvider provisionServiceProvider, FlagSource flagSource,
                                     NodeMetrics nodeMetrics, NodeMetricsDb nodeMetricsDb) {
        DefaultTimes defaults = new DefaultTimes(zone);

        nodeFailer = new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, defaults.failGrace, clock, orchestrator, throttlePolicyFromEnv().orElse(defaults.throttlePolicy), metric);
        periodicApplicationMaintainer = new PeriodicApplicationMaintainer(deployer, nodeRepository, defaults.redeployMaintainerInterval, defaults.periodicRedeployInterval);
        operatorChangeApplicationMaintainer = new OperatorChangeApplicationMaintainer(deployer, nodeRepository, defaults.operatorChangeRedeployInterval);
        reservationExpirer = new ReservationExpirer(nodeRepository, clock, defaults.reservationExpiry);
        retiredExpirer = new RetiredExpirer(nodeRepository, orchestrator, deployer, clock, defaults.retiredInterval, defaults.retiredExpiry);
        inactiveExpirer = new InactiveExpirer(nodeRepository, clock, defaults.inactiveExpiry);
        failedExpirer = new FailedExpirer(nodeRepository, zone, clock, defaults.failedExpirerInterval);
        dirtyExpirer = new DirtyExpirer(nodeRepository, clock, defaults.dirtyExpiry);
        provisionedExpirer = new ProvisionedExpirer(nodeRepository, clock, defaults.provisionedExpiry);
        nodeRebooter = new NodeRebooter(nodeRepository, clock, flagSource);
        metricsReporter = new MetricsReporter(nodeRepository, metric, orchestrator, serviceMonitor, periodicApplicationMaintainer::pendingDeployments, defaults.metricsInterval, clock);
        infrastructureProvisioner = new InfrastructureProvisioner(nodeRepository, infraDeployer, defaults.infrastructureProvisionInterval);
        loadBalancerExpirer = provisionServiceProvider.getLoadBalancerService().map(lbService ->
                new LoadBalancerExpirer(nodeRepository, defaults.loadBalancerExpirerInterval, lbService));
        dynamicProvisioningMaintainer = provisionServiceProvider.getHostProvisioner().map(hostProvisioner ->
                new DynamicProvisioningMaintainer(nodeRepository, defaults.dynamicProvisionerInterval, hostProvisioner, provisionServiceProvider.getHostResourcesCalculator(), flagSource));
        capacityReportMaintainer = new CapacityReportMaintainer(nodeRepository, metric, defaults.capacityReportInterval);
        osUpgradeActivator = new OsUpgradeActivator(nodeRepository, defaults.osUpgradeActivatorInterval);
        rebalancer = new Rebalancer(deployer, nodeRepository, provisionServiceProvider.getHostResourcesCalculator(), provisionServiceProvider.getHostProvisioner(), metric, clock, defaults.rebalancerInterval);
        nodeMetricsDbMaintainer = new NodeMetricsDbMaintainer(nodeRepository, nodeMetrics, nodeMetricsDb, defaults.nodeMetricsCollectionInterval);
        autoscalingMaintainer = new AutoscalingMaintainer(nodeRepository, provisionServiceProvider.getHostResourcesCalculator(), nodeMetricsDb, deployer, defaults.autoscalingInterval);

        // The DuperModel is filled with infrastructure applications by the infrastructure provisioner, so explicitly run that now
        infrastructureProvisioner.maintainButThrowOnException();
    }

    @Override
    public void deconstruct() {
        nodeFailer.deconstruct();
        periodicApplicationMaintainer.deconstruct();
        operatorChangeApplicationMaintainer.deconstruct();
        reservationExpirer.deconstruct();
        inactiveExpirer.deconstruct();
        retiredExpirer.deconstruct();
        failedExpirer.deconstruct();
        dirtyExpirer.deconstruct();
        nodeRebooter.deconstruct();
        capacityReportMaintainer.deconstruct();
        provisionedExpirer.deconstruct();
        metricsReporter.deconstruct();
        infrastructureProvisioner.deconstruct();
        loadBalancerExpirer.ifPresent(Maintainer::deconstruct);
        dynamicProvisioningMaintainer.ifPresent(Maintainer::deconstruct);
        osUpgradeActivator.deconstruct();
        rebalancer.deconstruct();
        nodeMetricsDbMaintainer.deconstruct();
        autoscalingMaintainer.deconstruct();
    }

    private static Optional<NodeFailer.ThrottlePolicy> throttlePolicyFromEnv() {
        String policyName = System.getenv(envPrefix + "throttle_policy");
        try {
            return Optional.ofNullable(policyName).map(NodeFailer.ThrottlePolicy::valueOf);
        } catch (IllegalArgumentException e) {
            log.info(String.format("Ignoring invalid throttle policy name: '%s'. Must be one of %s", policyName,
                                   Arrays.toString(NodeFailer.ThrottlePolicy.values())));
            return Optional.empty();
        }
    }

    private static class DefaultTimes {

        // TODO: Rename, kept now for compatibility reasons, want to change this and corresponding env variable
        /** Minimum time to wait between deployments by periodic application maintainer*/
        private final Duration periodicRedeployInterval;
        /** Time between each run of maintainer that does periodic redeployment */
        private final Duration redeployMaintainerInterval;
        /** Applications are redeployed after manual operator changes within this time period */
        private final Duration operatorChangeRedeployInterval;

        /** The time a node must be continuously nonresponsive before it is failed */
        private final Duration failGrace;
        
        private final Duration reservationExpiry;
        private final Duration inactiveExpiry;
        private final Duration retiredExpiry;
        private final Duration failedExpirerInterval;
        private final Duration dirtyExpiry;
        private final Duration provisionedExpiry;
        private final Duration capacityReportInterval;
        private final Duration metricsInterval;
        private final Duration retiredInterval;
        private final Duration infrastructureProvisionInterval;
        private final Duration loadBalancerExpirerInterval;
        private final Duration dynamicProvisionerInterval;
        private final Duration osUpgradeActivatorInterval;
        private final Duration rebalancerInterval;
        private final Duration nodeMetricsCollectionInterval;
        private final Duration autoscalingInterval;

        private final NodeFailer.ThrottlePolicy throttlePolicy;

        DefaultTimes(Zone zone) {
            failGrace = Duration.ofMinutes(30);
            periodicRedeployInterval = Duration.ofMinutes(30);
            // Don't redeploy in test environments
            redeployMaintainerInterval = Duration.ofMinutes(1);
            operatorChangeRedeployInterval = Duration.ofMinutes(1);
            failedExpirerInterval = Duration.ofMinutes(10);
            provisionedExpiry = Duration.ofHours(4);
            capacityReportInterval = Duration.ofMinutes(10);
            metricsInterval = Duration.ofMinutes(1);
            infrastructureProvisionInterval = Duration.ofMinutes(1);
            throttlePolicy = NodeFailer.ThrottlePolicy.hosted;
            loadBalancerExpirerInterval = Duration.ofMinutes(5);
            reservationExpiry = Duration.ofMinutes(15); // Need to be long enough for deployment to be finished for all config model versions
            dynamicProvisionerInterval = Duration.ofMinutes(5);
            osUpgradeActivatorInterval = zone.system().isCd() ? Duration.ofSeconds(30) : Duration.ofMinutes(5);
            rebalancerInterval = Duration.ofMinutes(40);
            nodeMetricsCollectionInterval = Duration.ofMinutes(1);
            autoscalingInterval = Duration.ofMinutes(5);

            if (zone.environment().equals(Environment.prod) && ! zone.system().isCd()) {
                inactiveExpiry = Duration.ofHours(4); // enough time for the application owner to discover and redeploy
                retiredInterval = Duration.ofMinutes(10);
                dirtyExpiry = Duration.ofHours(2); // enough time to clean the node
                retiredExpiry = Duration.ofDays(4); // give up migrating data after 4 days
            } else {
                inactiveExpiry = Duration.ofSeconds(2); // support interactive wipe start over
                retiredInterval = Duration.ofMinutes(1);
                dirtyExpiry = Duration.ofMinutes(30);
                retiredExpiry = Duration.ofMinutes(20);
            }
        }

    }

}
