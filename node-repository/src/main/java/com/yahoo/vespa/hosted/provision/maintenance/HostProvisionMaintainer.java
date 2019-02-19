// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
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
public class HostProvisionMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(HostProvisionMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;

    public HostProvisionMaintainer(
            NodeRepository nodeRepository, Duration interval, JobControl jobControl, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, jobControl);
        this.hostProvisioner = hostProvisioner;
    }

    @Override
    protected void maintain() {
        try (Mutex lock = nodeRepository().lockAllocation()) {
            NodeList nodes = nodeRepository().list();

            candidates(nodes).forEach((host, children) -> {
                try {
                    List<Node> updatedNodes = hostProvisioner.provision(host, children);
                    nodeRepository().write(updatedNodes);
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
    }

    /** @return map of set of children by parent Node, where parent is of type host and in state provisioned */
    static Map<Node, Set<Node>> candidates(NodeList nodes) {
        Map<String, Node> provisionedHostsByHostname = nodes.state(Node.State.provisioned).nodeType(NodeType.host)
                .asList().stream()
                .collect(Collectors.toMap(Node::hostname, Function.identity()));

        return nodes.asList().stream()
                .filter(node -> node.parentHostname().map(parent -> provisionedHostsByHostname.keySet().contains(parent)).orElse(false))
                .collect(Collectors.groupingBy(
                        node -> provisionedHostsByHostname.get(node.parentHostname().get()),
                        Collectors.toSet()));
    }
}
