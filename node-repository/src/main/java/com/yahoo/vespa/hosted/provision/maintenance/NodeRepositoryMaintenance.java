// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.AbstractComponent;
import com.yahoo.component.annotation.Inject;
import com.yahoo.concurrent.maintenance.Maintainer;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.InfraDeployer;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.autoscale.MetricsFetcher;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionServiceProvider;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A component which sets up all the node repo maintenance jobs.
 *
 * @author bratseth
 */
public class NodeRepositoryMaintenance extends AbstractComponent {

    private final List<Maintainer> maintainers = new CopyOnWriteArrayList<>();

    @SuppressWarnings("unused")
    @Inject
    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, InfraDeployer infraDeployer,
                                     ServiceMonitor serviceMonitor,
                                     Zone zone, Metric metric,
                                     ProvisionServiceProvider provisionServiceProvider, FlagSource flagSource,
                                     MetricsFetcher metricsFetcher) {
        DefaultTimes defaults = new DefaultTimes(zone, deployer);

        PeriodicApplicationMaintainer periodicApplicationMaintainer = new PeriodicApplicationMaintainer(deployer, metric, nodeRepository, defaults.redeployMaintainerInterval,
                                                                                                        defaults.periodicRedeployInterval, flagSource);
        InfrastructureProvisioner infrastructureProvisioner = new InfrastructureProvisioner(nodeRepository, infraDeployer, defaults.infrastructureProvisionInterval, metric);
        maintainers.add(periodicApplicationMaintainer);
        maintainers.add(infrastructureProvisioner);

        maintainers.add(new NodeFailer(deployer, nodeRepository, defaults.failGrace, defaults.nodeFailerInterval, defaults.throttlePolicy, metric));
        maintainers.add(new NodeHealthTracker(serviceMonitor, nodeRepository, defaults.nodeFailureStatusUpdateInterval, metric));
        maintainers.add(new ExpeditedChangeApplicationMaintainer(deployer, metric, nodeRepository, defaults.expeditedChangeRedeployInterval));
        maintainers.add(new ReservationExpirer(nodeRepository, defaults.reservationExpiry, metric));
        maintainers.add(new RetiredExpirer(nodeRepository, deployer, metric, defaults.retiredInterval, defaults.retiredExpiry));
        maintainers.add(new InactiveExpirer(nodeRepository, defaults.inactiveExpiry, metric));
        maintainers.add(new FailedExpirer(nodeRepository, zone, defaults.failedExpirerInterval, metric));
        maintainers.add(new DirtyExpirer(nodeRepository, defaults.dirtyExpiry, metric));
        maintainers.add(new ProvisionedExpirer(nodeRepository, defaults.provisionedExpiry, metric));
        maintainers.add(new NodeRebooter(nodeRepository, flagSource, metric));
        maintainers.add(new MetricsReporter(nodeRepository, metric, serviceMonitor, periodicApplicationMaintainer::pendingDeployments, defaults.metricsInterval));
        maintainers.add(new SpareCapacityMaintainer(deployer, nodeRepository, metric, defaults.spareCapacityMaintenanceInterval));
        maintainers.add(new OsUpgradeActivator(nodeRepository, defaults.osUpgradeActivatorInterval, metric));
        maintainers.add(new Rebalancer(deployer, nodeRepository, metric, defaults.rebalancerInterval));
        maintainers.add(new NodeMetricsDbMaintainer(nodeRepository, metricsFetcher, defaults.nodeMetricsCollectionInterval, metric));
        maintainers.add(new AutoscalingMaintainer(nodeRepository, deployer, metric, defaults.autoscalingInterval));
        maintainers.add(new ScalingSuggestionsMaintainer(nodeRepository, defaults.scalingSuggestionsInterval, metric));
        maintainers.add(new SwitchRebalancer(nodeRepository, defaults.switchRebalancerInterval, metric, deployer));

        provisionServiceProvider.getLoadBalancerService()
                                .map(lbService -> new LoadBalancerExpirer(nodeRepository, defaults.loadBalancerExpirerInterval, lbService, metric))
                                .ifPresent(maintainers::add);
        provisionServiceProvider.getHostProvisioner()
                                .map(hostProvisioner -> List.of(
                                        new HostCapacityMaintainer(nodeRepository, defaults.dynamicProvisionerInterval, hostProvisioner, flagSource, metric),
                                        new HostDeprovisioner(nodeRepository, defaults.hostDeprovisionerInterval, metric, hostProvisioner),
                                        new HostResumeProvisioner(nodeRepository, defaults.hostResumeProvisionerInterval, metric, hostProvisioner),
                                        new HostRetirer(nodeRepository, defaults.hostRetirerInterval, metric, hostProvisioner),
                                        new DiskReplacer(nodeRepository, defaults.diskReplacerInterval, metric, hostProvisioner)))
                                .ifPresent(maintainers::addAll);
        // The DuperModel is filled with infrastructure applications by the infrastructure provisioner, so explicitly run that now
        infrastructureProvisioner.maintainButThrowOnException();
    }

    @Override
    public void deconstruct() {
        maintainers.forEach(Maintainer::shutdown);
        maintainers.forEach(Maintainer::awaitShutdown);
    }

    private static class DefaultTimes {

        /** Minimum time to wait between deployments by periodic application maintainer*/
        private final Duration periodicRedeployInterval;
        /** Time between each run of maintainer that does periodic redeployment */
        private final Duration redeployMaintainerInterval;
        /** Applications are redeployed after manual operator changes within this time period */
        private final Duration expeditedChangeRedeployInterval;

        /** The time a node must be continuously unresponsive before it is failed */
        private final Duration failGrace;
        
        private final Duration reservationExpiry;
        private final Duration inactiveExpiry;
        private final Duration retiredExpiry;
        private final Duration failedExpirerInterval;
        private final Duration dirtyExpiry;
        private final Duration provisionedExpiry;
        private final Duration spareCapacityMaintenanceInterval;
        private final Duration metricsInterval;
        private final Duration nodeFailerInterval;
        private final Duration nodeFailureStatusUpdateInterval;
        private final Duration retiredInterval;
        private final Duration infrastructureProvisionInterval;
        private final Duration loadBalancerExpirerInterval;
        private final Duration dynamicProvisionerInterval;
        private final Duration hostDeprovisionerInterval;
        private final Duration hostResumeProvisionerInterval;
        private final Duration diskReplacerInterval;
        private final Duration osUpgradeActivatorInterval;
        private final Duration rebalancerInterval;
        private final Duration nodeMetricsCollectionInterval;
        private final Duration autoscalingInterval;
        private final Duration scalingSuggestionsInterval;
        private final Duration switchRebalancerInterval;
        private final Duration hostRetirerInterval;

        private final NodeFailer.ThrottlePolicy throttlePolicy;

        DefaultTimes(Zone zone, Deployer deployer) {
            boolean isCdZone = zone.system().isCd();

            autoscalingInterval = Duration.ofMinutes(5);
            dynamicProvisionerInterval = Duration.ofMinutes(3);
            hostDeprovisionerInterval = Duration.ofMinutes(3);
            hostResumeProvisionerInterval = Duration.ofMinutes(3);
            diskReplacerInterval = Duration.ofMinutes(3);
            failedExpirerInterval = Duration.ofMinutes(10);
            failGrace = Duration.ofMinutes(20);
            infrastructureProvisionInterval = Duration.ofMinutes(3);
            loadBalancerExpirerInterval = Duration.ofMinutes(5);
            metricsInterval = Duration.ofMinutes(1);
            nodeFailerInterval = Duration.ofMinutes(7);
            nodeFailureStatusUpdateInterval = Duration.ofMinutes(2);
            nodeMetricsCollectionInterval = Duration.ofMinutes(1);
            expeditedChangeRedeployInterval = Duration.ofMinutes(3);
            // Vespa upgrade frequency is higher in CD so (de)activate OS upgrades more frequently as well
            osUpgradeActivatorInterval = isCdZone ? Duration.ofSeconds(30) : Duration.ofMinutes(5);
            periodicRedeployInterval = Duration.ofMinutes(60);
            provisionedExpiry = zone.cloud().dynamicProvisioning() ? Duration.ofMinutes(40) : Duration.ofHours(4);
            rebalancerInterval = Duration.ofMinutes(120);
            redeployMaintainerInterval = Duration.ofMinutes(1);
            // Need to be long enough for deployment to be finished for all config model versions
            reservationExpiry = deployer.serverDeployTimeout();
            scalingSuggestionsInterval = Duration.ofMinutes(31);
            spareCapacityMaintenanceInterval = Duration.ofMinutes(30);
            switchRebalancerInterval = Duration.ofHours(1);
            throttlePolicy = NodeFailer.ThrottlePolicy.hosted;
            hostRetirerInterval = Duration.ofMinutes(30);

            if (zone.environment().isProduction() && ! isCdZone) {
                inactiveExpiry = Duration.ofHours(4); // enough time for the application owner to discover and redeploy
                retiredInterval = Duration.ofMinutes(15);
                dirtyExpiry = Duration.ofHours(2); // enough time to clean the node
                retiredExpiry = Duration.ofDays(4); // give up migrating data after 4 days
            } else {
                // long enough that nodes aren't reused immediately and delete can happen on all config servers
                // with time enough to clean up even with ZK connection issues on config servers
                inactiveExpiry = Duration.ofMinutes(1);
                dirtyExpiry = Duration.ofMinutes(30);
                // Longer time in non-CD since we might end up with many deployments in a short time
                // when retiring many hosts, e.g. when doing OS upgrades
                retiredInterval = isCdZone ? Duration.ofMinutes(1) : Duration.ofMinutes(5);
                retiredExpiry = Duration.ofDays(1);
            }
        }

    }

}
