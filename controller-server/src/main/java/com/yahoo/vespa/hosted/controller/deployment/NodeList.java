package com.yahoo.vespa.hosted.controller.deployment;


import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.config.provision.HostName;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;

public class NodeList extends AbstractFilteringList<NodeWithServices, NodeList> {

    private final long wantedConfigGeneration;

    private NodeList(Collection<? extends NodeWithServices> items, boolean negate, long wantedConfigGeneration) {
        super(items, negate, (i, n) -> new NodeList(i, n, wantedConfigGeneration));
        this.wantedConfigGeneration = wantedConfigGeneration;
    }

    public static NodeList of(List<Node> nodes, List<Node> parents, ServiceConvergence services) {
        var servicesByHostName = services.services().stream()
                                         .collect(groupingBy(service -> service.host()));
        var parentsByHostName = parents.stream()
                                       .collect(Collectors.toMap(node -> node.hostname(), node -> node));
        return new NodeList(nodes.stream()
                                 .map(node -> new NodeWithServices(node,
                                                                   parentsByHostName.get(node.parentHostname().get()),
                                                                   services.wantedGeneration(),
                                                                   servicesByHostName.get(node.hostname())))
                                 .collect(Collectors.toList()),
                            false,
                            services.wantedGeneration());
    }

    /** The nodes on an outdated OS. */
    public NodeList upgradingOs() {
        return matching(node -> node.parent().wantedOsVersion().isAfter(node.parent().currentOsVersion()));
    }

    /** The nodes whose parent is down. */
    public NodeList withParentDown() {
        return matching(node -> node.parent().serviceState() == Node.ServiceState.allowedDown);
    }

    /** The nodes on an outdated platform. */
    public NodeList upgradingPlatform() {
        return matching(node -> node.node().wantedVersion().isAfter(node.node().currentVersion()));
    }

    /** The nodes in need of a reboot. */
    public NodeList rebooting() {
        return matching(node -> node.node().wantedRebootGeneration() > node.node().rebootGeneration());
    }

    /** The nodes in need of a restart. */
    public NodeList restarting() {
        return matching(node -> node.node().wantedRestartGeneration() > node.node().restartGeneration());
    }

    /** The nodes currently allowed to be down. */
    public NodeList allowedDown() {
        return matching(node -> node.node().serviceState() == Node.ServiceState.allowedDown);
    }

    /** The nodes which have been suspended since before the given instant. */
    public NodeList suspendedSince(Instant instant) {
        return matching(node -> node.node().suspendedSince().map(instant::isAfter).orElse(false));
    }

    /** The nodes with services on outdated config generation. */
    public NodeList upgradingApplication() {
        return matching(node -> node.services().stream().anyMatch(service -> wantedConfigGeneration > service.currentGeneration()));
    }

    /** Returns a summary of the convergence status of the nodes in this list. */
    public ConvergenceSummary summary() {
        NodeList allowedDown = allowedDown();
        return new ConvergenceSummary(size(),
                                      allowedDown.size(),
                                      withParentDown().upgradingOs().size(),
                                      upgradingPlatform().size(),
                                      allowedDown.upgradingPlatform().size(),
                                      rebooting().size(),
                                      allowedDown.rebooting().size(),
                                      restarting().size(),
                                      allowedDown.restarting().size(),
                                      asList().stream().mapToLong(node -> node.services().size()).sum(),
                                      asList().stream().mapToLong(node -> node.services().stream().filter(service -> wantedConfigGeneration > service.currentGeneration()).count()).sum());
    }

}
