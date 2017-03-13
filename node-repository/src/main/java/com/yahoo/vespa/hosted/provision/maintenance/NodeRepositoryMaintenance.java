// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.google.inject.Inject;
import com.yahoo.component.AbstractComponent;
import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.HostLivenessTracker;
import com.yahoo.config.provision.Zone;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.curator.Curator;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.orchestrator.Orchestrator;
import com.yahoo.vespa.service.monitor.ServiceMonitor;

import java.time.Clock;
import java.time.Duration;
import java.util.Optional;

/**
 * A component which sets up all the node repo maintenance jobs.
 *
 * @author bratseth
 */
public class NodeRepositoryMaintenance extends AbstractComponent {

    private final NodeFailer nodeFailer;
    private final ApplicationMaintainer applicationMaintainer;
    private final ZooKeeperAccessMaintainer zooKeeperAccessMaintainer;
    private final ReservationExpirer reservationExpirer;
    private final InactiveExpirer inactiveExpirer;
    private final RetiredExpirer retiredExpirer;
    private final FailedExpirer failedExpirer;
    private final DirtyExpirer dirtyExpirer;
    private final NodeRebooter nodeRebooter;
    private final MetricsReporter metricsReporter;

    @Inject
    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Curator curator,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor, 
                                     Zone zone, Orchestrator orchestrator, Metric metric) {
        this(nodeRepository, deployer, curator, hostLivenessTracker, serviceMonitor, zone, Clock.systemUTC(), orchestrator, metric);
    }

    public NodeRepositoryMaintenance(NodeRepository nodeRepository, Deployer deployer, Curator curator,
                                     HostLivenessTracker hostLivenessTracker, ServiceMonitor serviceMonitor, 
                                     Zone zone, Clock clock, Orchestrator orchestrator, Metric metric) {
        DefaultTimes defaults = new DefaultTimes(zone.environment());
        nodeFailer = new NodeFailer(deployer, hostLivenessTracker, serviceMonitor, nodeRepository, fromEnv("fail_grace").orElse(defaults.failGrace), clock, orchestrator);
        applicationMaintainer = new ApplicationMaintainer(deployer, nodeRepository, fromEnv("redeploy_frequency").orElse(defaults.redeployFrequency));
        zooKeeperAccessMaintainer = new ZooKeeperAccessMaintainer(nodeRepository, curator, fromEnv("zookeeper_access_maintenance_interval").orElse(defaults.zooKeeperAccessMaintenanceInterval));
        reservationExpirer = new ReservationExpirer(nodeRepository, clock, fromEnv("reservation_expiry").orElse(defaults.reservationExpiry));
        retiredExpirer = new RetiredExpirer(nodeRepository, deployer, clock, fromEnv("retired_expiry").orElse(defaults.retiredExpiry));
        inactiveExpirer = new InactiveExpirer(nodeRepository, clock, fromEnv("inactive_expiry").orElse(defaults.inactiveExpiry));
        failedExpirer = new FailedExpirer(nodeRepository, zone, clock, fromEnv("failed_expiry").orElse(defaults.failedExpiry));
        dirtyExpirer = new DirtyExpirer(nodeRepository, clock, fromEnv("dirty_expiry").orElse(defaults.dirtyExpiry));
        nodeRebooter = new NodeRebooter(nodeRepository, clock, fromEnv("reboot_interval").orElse(defaults.rebootInterval));
        metricsReporter = new MetricsReporter(nodeRepository, metric, fromEnv("metrics_interval").orElse(defaults.metricsInterval));
    }

    private Optional<Duration> fromEnv(String envVariable) {
        String prefix = "vespa_node_repository__";
        return Optional.ofNullable(System.getenv(prefix + envVariable)).map(Long::parseLong).map(Duration::ofSeconds);
    }

    @Override
    public void deconstruct() {
        nodeFailer.deconstruct();
        applicationMaintainer.deconstruct();
        zooKeeperAccessMaintainer.deconstruct();
        reservationExpirer.deconstruct();
        inactiveExpirer.deconstruct();
        retiredExpirer.deconstruct();
        failedExpirer.deconstruct();
        dirtyExpirer.deconstruct();
        nodeRebooter.deconstruct();
        metricsReporter.deconstruct();
    }

    private static class DefaultTimes {

        /** All applications are redeployed with this frequency */
        private final Duration redeployFrequency;

        /** The time a node must be continuously nonresponsive before it is failed */
        private final Duration failGrace;
        
        private final Duration zooKeeperAccessMaintenanceInterval;

        private final Duration reservationExpiry;
        private final Duration inactiveExpiry;
        private final Duration retiredExpiry;
        private final Duration failedExpiry;
        private final Duration dirtyExpiry;
        private final Duration rebootInterval;
        private final Duration metricsInterval;

        DefaultTimes(Environment environment) {
            if (environment.equals(Environment.prod)) {
                // These values are to avoid losing data (retired), and to be able to return an application
                // back to a previous state fast (inactive)
                failGrace = Duration.ofMinutes(60);
                redeployFrequency = Duration.ofMinutes(30);
                zooKeeperAccessMaintenanceInterval = Duration.ofMinutes(1);
                reservationExpiry = Duration.ofMinutes(20); // same as deployment timeout
                inactiveExpiry = Duration.ofHours(4); // enough time for the application owner to discover and redeploy
                retiredExpiry = Duration.ofDays(4); // enough time to migrate data
                failedExpiry = Duration.ofDays(4); // enough time to recover data even if it happens friday night
                dirtyExpiry = Duration.ofHours(2); // enough time to clean the node
                rebootInterval = Duration.ofDays(30);
                metricsInterval = Duration.ofMinutes(1);
            } else {
                // These values ensure tests and development is not delayed due to nodes staying around
                // Use non-null values as these also determine the maintenance interval
                failGrace = Duration.ofMinutes(60);
                redeployFrequency = Duration.ofMinutes(30);
                zooKeeperAccessMaintenanceInterval = Duration.ofSeconds(10);
                reservationExpiry = Duration.ofMinutes(10); // Need to be long enough for deployment to be finished for all config model versions
                inactiveExpiry = Duration.ofSeconds(2); // support interactive wipe start over
                retiredExpiry = Duration.ofMinutes(1);
                failedExpiry = Duration.ofMinutes(10);
                dirtyExpiry = Duration.ofMinutes(30);
                rebootInterval = Duration.ofDays(30);
                metricsInterval = Duration.ofMinutes(1);
            }
        }

    }

}
