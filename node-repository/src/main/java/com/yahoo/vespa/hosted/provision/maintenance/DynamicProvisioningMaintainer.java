// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.flags.BooleanFlag;
import com.yahoo.vespa.flags.FlagSource;
import com.yahoo.vespa.flags.Flags;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.provisioning.FatalProvisioningException;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;
import com.yahoo.yolean.Exceptions;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class DynamicProvisioningMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(DynamicProvisioningMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;
    private final BooleanFlag dynamicProvisioningEnabled;

    DynamicProvisioningMaintainer(NodeRepository nodeRepository, Duration interval,
                                  HostProvisioner hostProvisioner, FlagSource flagSource) {
        super(nodeRepository, interval);
        this.hostProvisioner = hostProvisioner;
        this.dynamicProvisioningEnabled = Flags.ENABLE_DYNAMIC_PROVISIONING.bindTo(flagSource);
    }

    @Override
    protected void maintain() {
        if (! dynamicProvisioningEnabled.value()) return;

        try (Mutex lock = nodeRepository().lockAllocation()) {
            NodeList nodes = nodeRepository().list();

            updateProvisioningNodes(nodes, lock);
            deprovisionExcess(nodes);
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

    void deprovisionExcess(NodeList nodes) {
        for (Node node : getEmptyHosts(nodes)) {
            try {
                hostProvisioner.deprovision(node);
                nodeRepository().removeRecursively(node, true);
            } catch (RuntimeException e) {
                log.log(Level.WARNING, "Failed to deprovision " + node.hostname() + ", will retry in " + interval(), e);
            }
        }
    }

    /** @return Nodes of type host, in any state, that have no children with allocation */
    static Set<Node> getEmptyHosts(NodeList nodes) {
        Map<String, Node> hostsByHostname = nodes.nodeType(NodeType.host)
                .asList().stream()
                .collect(Collectors.toMap(Node::hostname, Function.identity()));

        nodes.asList().stream()
                .filter(node -> node.allocation().isPresent())
                .flatMap(node -> node.parentHostname().stream())
                .distinct()
                .forEach(hostsByHostname::remove);

        return Set.copyOf(hostsByHostname.values());
    }
}
