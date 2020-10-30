// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.OutOfCapacityException;
import com.yahoo.jdisc.Metric;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.flags.ListFlag;
import com.yahoo.vespa.flags.custom.HostCapacity;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner.HostSharing;
import com.yahoo.vespa.hosted.provision.provisioning.NodeResourceComparator;
import com.yahoo.vespa.hosted.provision.provisioning.ProvisionedHost;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
 * @author mpolden
 */
public class DynamicProvisioningMaintainer extends NodeRepositoryMaintainer {

    private static final Logger log = Logger.getLogger(DynamicProvisioningMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;
    private final ListFlag<HostCapacity> targetCapacityFlag;

    DynamicProvisioningMaintainer(NodeRepository nodeRepository,
                                  Duration interval,
                                  HostProvisioner hostProvisioner,
                                  FlagSource flagSource,
                                  Metric metric) {
        super(nodeRepository, interval, metric);
        this.hostProvisioner = hostProvisioner;
        this.targetCapacityFlag = Flags.TARGET_CAPACITY.bindTo(flagSource);
    }

    @Override
    protected boolean maintain() {
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            NodeList nodes = nodeRepository().list();
            resumeProvisioning(nodes, lock);
            convergeToCapacity(nodes);
        }
        return true;
    }

    /** Resume provisioning of already provisioned hosts and their children */
    private void resumeProvisioning(NodeList nodes, Mutex lock) {
        Map<String, Set<Node>> nodesByProvisionedParentHostname = nodes.nodeType(NodeType.tenant).asList().stream()
                                                                       .filter(node -> node.parentHostname().isPresent())
                                                                       .collect(Collectors.groupingBy(
                                                                               node -> node.parentHostname().get(),
                                                                               Collectors.toSet()));

        nodes.state(Node.State.provisioned).hosts().forEach(host -> {
            Set<Node> children = nodesByProvisionedParentHostname.getOrDefault(host.hostname(), Set.of());
            try {
                List<Node> updatedNodes = hostProvisioner.provision(host, children);
                verifyDns(updatedNodes);
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
        List<NodeResources> capacity = targetCapacity();
        List<Node> excessHosts = provision(capacity, nodes);
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
     * Provision hosts to ensure there is room to allocate spare nodes.
     *
     * @param advertisedSpareCapacity the advertised resources of the spare nodes
     * @param nodes list of all nodes
     * @return excess hosts that can safely be deprovisioned: An excess host 1. contains no nodes allocated
     *         to an application, and assuming the spare nodes have been allocated, and 2. is not parked
     *         without wantToDeprovision (which means an operator is looking at the node).
     */
    private List<Node> provision(List<NodeResources> advertisedSpareCapacity, NodeList nodes) {
        Map<String, Node> hostsByHostname = new HashMap<>(nodes.hosts().asList().stream()
                .filter(host -> host.state() != Node.State.parked || host.status().wantToDeprovision())
                .collect(Collectors.toMap(Node::hostname, Function.identity())));

        nodes.asList().stream()
                .filter(node -> node.allocation().isPresent())
                .flatMap(node -> node.parentHostname().stream())
                .distinct()
                .forEach(hostsByHostname::remove);

        List<Node> excessHosts = new ArrayList<>(hostsByHostname.values());

        var capacity = new ArrayList<>(advertisedSpareCapacity);
        for (Iterator<NodeResources> it = capacity.iterator(); it.hasNext() && !excessHosts.isEmpty(); ) {
            NodeResources resources = it.next();
            excessHosts.stream()
                       .filter(nodeRepository()::canAllocateTenantNodeTo)
                       .filter(host -> nodeRepository().resourcesCalculator()
                                                       .advertisedResourcesOf(host.flavor())
                                                       .satisfies(resources))
                       .min(Comparator.comparingInt(n -> n.flavor().cost()))
                       .ifPresent(host -> {
                           excessHosts.remove(host);
                           it.remove();
                       });
        }

        // Pre-provisioning is best effort, do one host at a time
        capacity.forEach(resources -> {
            try {
                Version osVersion = nodeRepository().osVersions().targetFor(NodeType.host).orElse(Version.emptyVersion);
                List<Node> hosts = hostProvisioner.provisionHosts(nodeRepository().database().getProvisionIndexes(1),
                                                                  resources, ApplicationId.defaultId(), osVersion,
                                                                  HostSharing.shared)
                                                  .stream()
                                                  .map(ProvisionedHost::generateHost)
                                                  .collect(Collectors.toList());
                nodeRepository().addNodes(hosts, Agent.DynamicProvisioningMaintainer);
            } catch (OutOfCapacityException | IllegalArgumentException | IllegalStateException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ": " + e.getMessage());
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to pre-provision " + resources + ", will retry in " + interval(), e);
            }
        });

        return excessHosts;
    }


    /** Reads node resources declared by target capacity flag */
    private List<NodeResources> targetCapacity() {
        return targetCapacityFlag.value().stream()
                                 .flatMap(cap -> {
                                     NodeResources resources = new NodeResources(cap.getVcpu(), cap.getMemoryGb(),
                                                                                 cap.getDiskGb(), 1);
                                     return IntStream.range(0, cap.getCount()).mapToObj(i -> resources);
                                 })
                                 .sorted(NodeResourceComparator.memoryDiskCpuOrder().reversed())
                                 .collect(Collectors.toList());
    }

    /** Verify DNS configuration of given nodes */
    private void verifyDns(List<Node> nodes) {
        for (var node : nodes) {
            for (var ipAddress : node.ipConfig().primary()) {
                IP.verifyDns(node.hostname(), ipAddress, nodeRepository().nameResolver());
            }
        }
    }
}
