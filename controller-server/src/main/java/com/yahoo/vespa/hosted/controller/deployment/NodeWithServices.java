// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.controller.deployment;

import com.yahoo.component.Version;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.Node;
import com.yahoo.vespa.hosted.controller.api.integration.configserver.ServiceConvergence;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import static java.util.Objects.requireNonNull;

/**
 * Aggregate of a node and its services, fetched from different sources.
 *
 * @author jonmv
 */
public class NodeWithServices {

    private final Node node;
    private final Node parent;
    private final long wantedConfigGeneration;
    private final List<ServiceConvergence.Status> services;

    public NodeWithServices(Node node, Node parent, long wantedConfigGeneration, List<ServiceConvergence.Status> services) {
        this.node = requireNonNull(node);
        this.parent = requireNonNull(parent);
        if (wantedConfigGeneration <= 0)
            throw new IllegalArgumentException("Wanted config generation must be positive");
        this.wantedConfigGeneration = wantedConfigGeneration;
        this.services = List.copyOf(services);
    }

    public Node node() { return node; }
    public Node parent() { return parent; }
    public long wantedConfigGeneration() { return wantedConfigGeneration; }
    public List<ServiceConvergence.Status> services() { return services; }

    public boolean needsOsUpgrade() {
        return parent.wantedOsVersion().isAfter(parent.currentOsVersion());
    }

    public boolean needsFirmwareUpgrade(){
        return parent.wantedFirmwareCheck()
                     .map(wanted -> parent.currentFirmwareCheck()
                                          .map(wanted::isAfter)
                                          .orElse(true))
                     .orElse(false);
    }

    public boolean hasParentDown() {
        return parent.serviceState() == Node.ServiceState.allowedDown;
    }

    public boolean needsPlatformUpgrade() {
        return node.wantedVersion().isAfter(node.currentVersion())
                || ! node.wantedDockerImage().equals(node.currentDockerImage());
    }

    public boolean needsReboot() {
        return node.wantedRebootGeneration() > node.rebootGeneration();
    }

    public boolean needsRestart() {
        return node.wantedRestartGeneration() > node.restartGeneration();
    }

    public boolean isAllowedDown() {
        return node.serviceState() == Node.ServiceState.allowedDown;
    }

    public boolean isNewlyProvisioned() {
        return node.currentVersion().equals(Version.emptyVersion);
    }

    public boolean isSuspendedSince(Instant instant) {
        return node.suspendedSince().map(instant::isAfter).orElse(false);
    }

    public boolean needsNewConfig() {
        return services.stream().anyMatch(service -> wantedConfigGeneration > service.currentGeneration());
    }

    public boolean isStateful() {
        return node.clusterType() == Node.ClusterType.content || node.clusterType() == Node.ClusterType.combined;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeWithServices that = (NodeWithServices) o;
        return node.equals(that.node);
    }

    @Override
    public int hashCode() {
        return Objects.hash(node);
    }

}
