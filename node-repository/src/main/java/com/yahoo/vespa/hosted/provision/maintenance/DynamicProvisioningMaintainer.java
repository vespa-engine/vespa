// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.custom.PreprovisionCapacity;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.NodePrioritizer;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceComparator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * @author freva
 */
public class DynamicProvisioningMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DynamicProvisioningMaintainer.class.getName());
    private static final ApplicationId preprovisionAppId = ApplicationId.from("hosted-vespa", "tenant-host", "preprovision");

    private final HostProvisioner hostProvisioner;
    private final BooleanFlag dynamicProvisioningEnabled;
    private final ListFlag<PreprovisionCapacity> preprovisionCapacityFlag;

    DynamicProvisioningMaintainer(NodeRepository nodeRepository, Duration interval,
                                  HostProvisioner hostProvisioner, FlagSource flagSource) {
        super(nodeRepository, interval);
        this.hostProvisioner = hostProvisioner;
        this.dynamicProvisioningEnabled = Flags.ENABLE_DYNAMIC_PROVISIONING.bindTo(flagSource);
        this.preprovisionCapacityFlag = Flags.PREPROVISION_CAPACITY.bindTo(flagSource);
    }

    @Override
    protected void maintain() {
        if (! dynamicProvisioningEnabled.value()) return;

        try (Mutex lock = nodeRepository().lockAllocation()) {
            NodeList nodes = nodeRepository().list();

            updateProvisioningNodes(nodes, lock);
            convergeToCapacity(nodes);
        }
    }

    void updateProvisioningNodes(NodeList nodes, Mutex lock) {
        Map<String, Node> provisionedHostsByHostname = nodes.state(Node.State.provisioned).nodeType(NodeType.host)
                .asList().stream()
                .collect(Collectors.toMap(Node::hostname, Function.identity()));

        Map<Node, Set<Node>> nodesByProvisionedParent = nodes.asList().stream()
                .filter(node -> node.parentHostname().map(provisionedHostsByHostname::containsKey).orElse(false))
                .collect(Collectors.groupingBy(
                        node -> provisionedHostsByHostname.get(node.parentHostname().get()),
                        Collectors.toSet()));

        nodesByProvisionedParent.forEach((host, children) -> {
            try {
                List<Node> updatedNodes = hostProvisioner.provision(host, children);
                nodeRepository().write(updatedNodes, lock);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(Level.INFO, "Failed to provision " + host.hostname() + ": " + Exceptions.toMessageString(e));
            } catch (FatalProvisioningException e) {
                log.log(Level.SEVERE, "Failed to provision " + host.hostname() + ", failing out the host recursively", e);
                // Fail out as operator to force a quick redeployment
                nodeRepository().failRecursively(
                        host.hostname(), Agent.operator, "Failed by HostProvisioner due to provisioning failure");
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to provision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }

    void convergeToCapacity(NodeList nodes) {
        List<Node> emptyHosts = getAllocatableHosts(nodes);
        List<NodeResources> preProvisionCapacity = preprovisionCapacityFlag.value().stream()
                .flatMap(cap -> {
                    NodeResources resources = new NodeResources(cap.getVcpu(), cap.getMemoryGb(), cap.getDiskGb(), 1);
                    return IntStream.range(0, cap.getCount()).mapToObj(i -> resources);
                })
                .sorted(NodeResourceComparator.memoryDiskCpuOrder())
                .collect(Collectors.toList());

        int hostI = 0;
        int capacityI = 0;
        while (hostI < emptyHosts.size() && capacityI < preProvisionCapacity.size()) {
            if (NodePrioritizer.ALLOCATABLE_HOST_STATES.contains(emptyHosts.get(hostI).state()) &&
                    emptyHosts.get(hostI).flavor().resources().satisfies(preProvisionCapacity.get(capacityI))) {
                emptyHosts.remove(hostI);
                preProvisionCapacity.remove(capacityI);
            } else {
                // If the host is not in one of the allocatable states or the host does not satisfy any of the
                // pre-provisioning reservations, keep it in the list so it can be deprovisioned
                hostI++;
            }
        }

        // pre-provisioning is best effort, do one host at the time
        preProvisionCapacity.forEach(resources -> {
            try {
                List<Node> hosts = hostProvisioner.provisionHosts(
                        nodeRepository().database().getProvisionIndexes(1), resources, preprovisionAppId).stream()
                        .map(ProvisionedHost::generateHost)
                        .collect(Collectors.toList());
                nodeRepository().addNodes(hosts);
            } catch (OutOfCapacityException | IllegalArgumentException | IllegalStateException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ":" + e.getMessage());
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ", will retry in " + interval(), e);
            }
        });

        // Finally, deprovision excess hosts.
        emptyHosts.forEach(host -> {
            try {
                hostProvisioner.deprovision(host);
                nodeRepository().removeRecursively(host, true);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to deprovision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }

    /** @return Nodes of type host, in any state, that have no children with allocation */
    private static List<Node> getAllocatableHosts(NodeList nodes) {
        Map<String, Node> hostsByHostname = nodes.nodeType(NodeType.host)
                .asList().stream()
                .collect(Collectors.toMap(Node::hostname, Function.identity()));

        nodes.asList().stream()
                .filter(node -> node.allocation().isPresent())
                .flatMap(node -> node.parentHostname().stream())
                .distinct()
                .forEach(hostsByHostname::remove);

        return hostsByHostname.values().stream()
                .sorted((n1, n2) -> NodeResourceComparator.memoryDiskCpuOrder().compare(n1.flavor().resources(), n2.flavor().resources()))
                .collect(Collectors.toList());
    }
}
