// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.transaction.Mutex;
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
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceComparator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
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
public class DynamicProvisioningMaintainer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(DynamicProvisioningMaintainer.class.getName());
    private static final ApplicationId preprovisionAppId = ApplicationId.from("hosted-vespa", "tenant-host", "preprovision");

    private final HostProvisioner hostProvisioner;
    private final ListFlag<PreprovisionCapacity> preprovisionCapacityFlag;

    DynamicProvisioningMaintainer(NodeRepository nodeRepository,
                                  Duration interval,
                                  HostProvisioner hostProvisioner,
                                  FlagSource flagSource) {
        super(nodeRepository, interval);
        this.hostProvisioner = hostProvisioner;
        this.preprovisionCapacityFlag = Flags.PREPROVISION_CAPACITY.bindTo(flagSource);
    }

    @Override
    protected void maintain() {
        if (! nodeRepository().zone().getCloud().dynamicProvisioning()) return;

        try (Mutex lock = nodeRepository().lockUnallocated()) {
            NodeList nodes = nodeRepository().list();
            resumeProvisioning(nodes, lock);
            convergeToCapacity(nodes);
        }
    }

    /** Resume provisioning of already provisioned hosts and their children */
    private void resumeProvisioning(NodeList nodes, Mutex lock) {
        Map<String, Set<Node>> nodesByProvisionedParentHostname = nodes.nodeType(NodeType.tenant).asList().stream()
                .filter(node -> node.parentHostname().isPresent())
                .collect(Collectors.groupingBy(
                        node -> node.parentHostname().get(),
                        Collectors.toSet()));

        nodes.state(Node.State.provisioned).nodeType(NodeType.host).forEach(host -> {
            Set<Node> children = nodesByProvisionedParentHostname.getOrDefault(host.hostname(), Set.of());
            try {
                List<Node> updatedNodes = hostProvisioner.provision(host, children);
                nodeRepository().write(updatedNodes, lock);
            } catch (IllegalArgumentException | IllegalStateException e) {
                log.log(Level.INFO, "Failed to provision " + host.hostname() + " with " + children.size() + " children: " +
                                    Exceptions.toMessageString(e));
            } catch (FatalProvisioningException e) {
                log.log(Level.SEVERE, "Failed to provision " + host.hostname() + " with " + children.size()  +
                                      " children, failing out the host recursively", e);
                // Fail out as operator to force a quick redeployment
                nodeRepository().failRecursively(
                        host.hostname(), Agent.operator, "Failed by HostProvisioner due to provisioning failure");
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to provision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }

    /** Converge zone to wanted capacity */
    private void convergeToCapacity(NodeList nodes) {
        List<Node> removableHosts = removableHostsOf(nodes);
        List<Node> excessHosts = preprovisionCapacity(removableHosts);

        excessHosts.forEach(host -> {
            try {
                hostProvisioner.deprovision(host);
                nodeRepository().removeRecursively(host, true);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to deprovision " + host.hostname() + ", will retry in " + interval(), e);
            }
        });
    }


    /**
     * Provision spare capacity according to removable hosts.
     *
     * @return Excess hosts eligible for deprovisioning.
     */
    private List<Node> preprovisionCapacity(List<Node> removableHosts) {
        List<Node> excessHosts = new ArrayList<>(removableHosts);
        List<NodeResources> spareCapacity = preprovisionedCapacity();

        // Keep one removable host, if that host satisfies the capacity requirement. This results in one host being
        // empty most of the time.
        for (Iterator<NodeResources> it = spareCapacity.iterator(); it.hasNext() && !excessHosts.isEmpty(); ) {
            NodeResources resources = it.next();
            excessHosts.stream()
                       .filter(nodeRepository()::canAllocateTenantNodeTo)
                       .filter(host -> nodeRepository().resourcesCalculator().advertisedResourcesOf(host.flavor()).satisfies(resources))
                       .min(Comparator.comparingInt(n -> n.flavor().cost()))
                       .ifPresent(host -> {
                           excessHosts.remove(host);
                           it.remove();
                       });
        }

        // Pre-provisioning is best effort, do one host at a time
        spareCapacity.forEach(resources -> {
            try {
                Version osVersion = nodeRepository().osVersions().targetFor(NodeType.host).orElse(Version.emptyVersion);
                List<Node> hosts = hostProvisioner.provisionHosts(nodeRepository().database().getProvisionIndexes(1),
                                                                  resources, preprovisionAppId, osVersion)
                                                  .stream()
                                                  .map(ProvisionedHost::generateHost)
                                                  .collect(Collectors.toList());
                nodeRepository().addNodes(hosts, Agent.DynamicProvisioningMaintainer);
            } catch (OutOfCapacityException | IllegalArgumentException | IllegalStateException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ":" + e.getMessage());
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ", will retry in " + interval(), e);
            }
        });

        return excessHosts;
    }

    /** Returns the preprovisioned capacity that should be available in this zone, if any */
    private List<NodeResources> preprovisionedCapacity() {
        return preprovisionCapacityFlag.value().stream()
                                       .flatMap(cap -> {
                                           NodeResources resources = new NodeResources(cap.getVcpu(), cap.getMemoryGb(),
                                                                                       cap.getDiskGb(), 1);
                                           return IntStream.range(0, cap.getCount()).mapToObj(i -> resources);
                                       })
                                       .sorted(NodeResourceComparator.memoryDiskCpuOrder().reversed())
                                       .collect(Collectors.toList());
    }

    /** Returns hosts that are candidates for removal, e.g. hosts that have no containers or are failed */
    private static List<Node> removableHostsOf(NodeList nodes) {
        Map<String, Node> hostsByHostname = nodes.nodeType(NodeType.host)
                                                 .matching(host -> host.state() != Node.State.parked ||
                                                                   host.status().wantToDeprovision())
                                                 .stream()
                                                 .collect(Collectors.toMap(Node::hostname, Function.identity()));

        nodes.asList().stream()
             .filter(node -> node.allocation().isPresent())
             .flatMap(node -> node.parentHostname().stream())
             .distinct()
             .forEach(hostsByHostname::remove);

        return List.copyOf(hostsByHostname.values());
    }

}
