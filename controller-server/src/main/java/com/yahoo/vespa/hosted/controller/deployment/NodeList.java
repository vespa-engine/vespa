// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.collections.AbstractFilteringList;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author jonmv
 */
public class NodeList extends AbstractFilteringList<NodeWithServices, NodeList> {

    private final long wantedConfigGeneration;

    private NodeList(Collection<? extends NodeWithServices> items, boolean negate, long wantedConfigGeneration) {
        super(items, negate, (i, n) -> new NodeList(i, n, wantedConfigGeneration));
        this.wantedConfigGeneration = wantedConfigGeneration;
    }

    public static NodeList of(List<Node> nodes, List<Node> parents, ServiceConvergence services) {
        var servicesByHostName = services.services().stream()
                                         .collect(Collectors.groupingBy(service -> service.host()));
        var parentsByHostName = parents.stream()
                                       .collect(Collectors.toMap(node -> node.hostname(), node -> node));
        return new NodeList(nodes.stream()
                                 .map(node -> new NodeWithServices(node,
                                                                   parentsByHostName.get(node.parentHostname().get()),
                                                                   services.wantedGeneration(),
                                                                   servicesByHostName.getOrDefault(node.hostname(), List.of())))
                                 .collect(Collectors.toList()),
                            false,
                            services.wantedGeneration());
    }

    /** The nodes on an outdated OS. */
    public NodeList needsOsUpgrade() {
        return matching(NodeWithServices::needsOsUpgrade);
    }

    /** The nodes with outdated firmware. */
    public NodeList needsFirmwareUpgrade() {
        return matching(NodeWithServices::needsFirmwareUpgrade);
    }

    /** The nodes whose parent is down. */
    public NodeList withParentDown() {
        return matching(NodeWithServices::hasParentDown);
    }

    /** The nodes on an outdated platform. */
    public NodeList needsPlatformUpgrade() {
        return matching(NodeWithServices::needsPlatformUpgrade);
    }

    /** The nodes in need of a reboot. */
    public NodeList needsReboot() {
        return matching(NodeWithServices::needsReboot);
    }

    /** The nodes in need of a restart. */
    public NodeList needsRestart() {
        return matching(NodeWithServices::needsRestart);
    }

    /** The nodes currently allowed to be down. */
    public NodeList allowedDown() {
        return matching(node -> node.isAllowedDown());
    }

    /** The nodes currently expected to be down. */
    public NodeList expectedDown() {
        return matching(node -> node.isAllowedDown() || node.isNewlyProvisioned());
    }

    /** The nodes which have been suspended since before the given instant. */
    public NodeList suspendedSince(Instant instant) {
        return matching(node -> node.isSuspendedSince(instant));
    }

    /** The nodes with services on outdated config generation. */
    public NodeList needsNewConfig() {
        return matching(NodeWithServices::needsNewConfig);
    }

    /** Returns a summary of the convergence status of the nodes in this list. */
    public ConvergenceSummary summary() {
        NodeList allowedDown = expectedDown();
        return new ConvergenceSummary(size(),
                                      allowedDown.size(),
                                      withParentDown().needsOsUpgrade().size(),
                                      withParentDown().needsFirmwareUpgrade().size(),
                                      needsPlatformUpgrade().size(),
                                      allowedDown.needsPlatformUpgrade().size(),
                                      needsReboot().size(),
                                      allowedDown.needsReboot().size(),
                                      needsRestart().size(),
                                      allowedDown.needsRestart().size(),
                                      asList().stream().mapToLong(node -> node.services().size()).sum(),
                                      asList().stream().mapToLong(node -> node.services().stream().filter(service -> wantedConfigGeneration > service.currentGeneration()).count()).sum());
    }

}
