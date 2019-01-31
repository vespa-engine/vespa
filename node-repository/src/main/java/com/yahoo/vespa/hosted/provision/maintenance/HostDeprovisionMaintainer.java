// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.maintenance;

import com.yahoo.config.provision.NodeType;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.provisioning.HostProvisioner;

import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * @author freva
 */
public class HostDeprovisionMaintainer extends Maintainer {

    private static final Logger log = Logger.getLogger(HostDeprovisionMaintainer.class.getName());

    private final HostProvisioner hostProvisioner;

    public HostDeprovisionMaintainer(
            NodeRepository nodeRepository, Duration interval, JobControl jobControl, HostProvisioner hostProvisioner) {
        super(nodeRepository, interval, jobControl);
        this.hostProvisioner = hostProvisioner;
    }

    @Override
    protected void maintain() {
        try (Mutex lock = nodeRepository().lockUnallocated()) {
            NodeList nodes = nodeRepository().list();

            for (Node node : candidates(nodes)) {
                try {
                    hostProvisioner.deprovision(node);
                    nodeRepository().removeRecursively(node.hostname());
                } catch (RuntimeException e) {
                    log.log(Level.WARNING, "Failed to deprovision " + node.hostname() + ", will retry in " + interval(), e);
                }
            }
        }
    }

    /** @return Nodes of type host, in any state, that have no children with allocation */
    static Set<Node> candidates(NodeList nodes) {
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
