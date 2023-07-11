// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.Deployer;
import com.yahoo.config.provision.NodeAllocationException;
import com.yahoo.config.provision.NodeType;
import com.yahoo.jdisc.Metric;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.logging.Level;

/**
 * This maintainer attempts to upgrade a single host running on an older flavor generation. The upgrade happens by
 * marking and retiring the host on the old generation, and redeploying to provision a replacement host on a newer
 * generation.
 *
 * If the cloud provider reports a lack of capacity for the newer generation, retirement of the host is
 * cancelled, and upgrade is attempted of the next host on an old flavor, if any.
 *
 * Once a host has been marked for upgrade, {@link HostResumeProvisioner} will complete provisioning of the replacement
 * host.
 *
 * @author mpolden
 */
public class HostFlavorUpgrader extends NodeRepositoryMaintainer {

    private final HostProvisioner hostProvisioner;
    private final Random random;
    private final Deployer deployer;
    private final Metric metric;

    public HostFlavorUpgrader(NodeRepository nodeRepository, Duration interval, Metric metric, Deployer deployer, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = Objects.requireNonNull(hostProvisioner);
        this.deployer = Objects.requireNonNull(deployer);
        this.metric = Objects.requireNonNull(metric);
        this.random = new Random(nodeRepository.clock().millis());
    }

    @Override
    protected double maintain() {
        if (!nodeRepository().zone().cloud().dynamicProvisioning()) return 1.0; // Not relevant in zones with static capacity
        if (nodeRepository().zone().environment().isTest()) return 1.0; // Short-lived deployments
        if (!nodeRepository().nodes().isWorking()) return 0.0;

        NodeList allNodes = nodeRepository().nodes().list();
        if (!NodeMover.zoneIsStable(allNodes)) return 1.0;
        return upgradeHostFlavor(allNodes);
    }

    private double upgradeHostFlavor(NodeList allNodes) {
        NodeList activeNodes = allNodes.nodeType(NodeType.tenant)
                                       .state(Node.State.active)
                                       .shuffle(random); // Shuffle to avoid getting stuck trying to upgrade the same host
        for (var node : activeNodes) {
            Optional<Node> parent = allNodes.parentOf(node);
            if (parent.isEmpty()) continue;
            if (!hostProvisioner.canUpgradeFlavor(parent.get(), node)) continue;
            if (parent.get().status().wantToUpgradeFlavor()) continue; // Already upgrading

            boolean redeployed = false;
            boolean deploymentValid = false;
            try (MaintenanceDeployment deployment = new MaintenanceDeployment(node.allocation().get().owner(), deployer, metric, nodeRepository(), true)) {
                deploymentValid = deployment.isValid();
                if (!deploymentValid) continue;

                log.log(Level.INFO, () -> "Redeploying " + node.allocation().get().owner() + " to upgrade flavor (" +
                                          parent.get().flavor().name() + ") of " + parent.get());
                upgradeFlavor(parent.get(), true);
                deployment.activate();
                redeployed = true;
                return 1.0;
            } catch (NodeAllocationException e) {
               // Fine, no capacity for upgrade
            } finally {
                if (deploymentValid && !redeployed) { // Cancel upgrade if redeploy failed
                    upgradeFlavor(parent.get(), false);
                }
            }
        }
        return 1.0;
    }

    private void upgradeFlavor(Node host, boolean upgrade) {
        nodeRepository().nodes().upgradeFlavor(host.hostname(),
                                               Agent.HostFlavorUpgrader,
                                               nodeRepository().clock().instant(),
                                               upgrade);
    }

}
