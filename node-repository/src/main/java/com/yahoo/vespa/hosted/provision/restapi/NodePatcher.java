// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.google.common.base.Suppliers;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.io.IOUtils;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.transaction.Mutex;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.yahoo.config.provision.NodeResources.DiskSpeed.fast;
import static com.yahoo.config.provision.NodeResources.DiskSpeed.slow;
import static com.yahoo.config.provision.NodeResources.StorageType.local;
import static com.yahoo.config.provision.NodeResources.StorageType.remote;

/**
 * A class which can take a partial JSON node/v2 node JSON structure and apply it to a node object.
 * This is a one-time use object.
 *
 * @author bratseth
 */
public class NodePatcher implements AutoCloseable {

    private static final String WANT_TO_RETIRE = "wantToRetire";
    private static final String WANT_TO_DEPROVISION = "wantToDeprovision";
    private static final Set<String> RECURSIVE_FIELDS = Set.of(WANT_TO_RETIRE);

    private final NodeRepository nodeRepository;
    private final com.google.common.base.Supplier<LockedNodeList> memoizedNodes;
    private final PatchedNodes patchedNodes;
    private final NodeFlavors nodeFlavors;
    private final Inspector inspector;
    private final Clock clock;

    public NodePatcher(NodeFlavors nodeFlavors, InputStream json, Node node, NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
        this.nodeFlavors = nodeFlavors;
        this.clock = nodeRepository.clock();
        try {
            this.inspector = SlimeUtils.jsonToSlime(IOUtils.readBytes(json, 1000 * 1000)).get();
        } catch (IOException e) {
            throw new UncheckedIOException("Error reading request body", e);
        }

        this.patchedNodes = new PatchedNodes(nodeRepository.nodes().lockAndGetRequired(node));
        try {
            this.memoizedNodes = Suppliers.memoize(() -> nodeRepository.nodes().list(patchedNodes.nodeMutex()));
        } catch (RuntimeException e) {
            patchedNodes.close();
            throw e;
        }
    }

    /**
     * Apply the json to the node and return all nodes affected by the patch.
     * More than 1 node may be affected if e.g. the node is a Docker host, which may have
     * children that must be updated in a consistent manner.
     */
    public List<Node> apply() {
        inspector.traverse((String name, Inspector value) -> {
            try {
                patchedNodes.update(applyField(patchedNodes.node(), name, value, inspector, false));

                if (RECURSIVE_FIELDS.contains(name)) {
                    for (Node child: patchedNodes.children()) {
                        patchedNodes.update(applyField(child, name, value, inspector, true));
                    }
                }
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Could not set field '" + name + "'", e);
            }
        });

        return patchedNodes.nodes();
    }

    public NodeMutex nodeMutexOfHost() { return patchedNodes.nodeMutex(); }

    @Override
    public void close() {
        patchedNodes.close();
    }

    private Node applyField(Node node, String name, Inspector value, Inspector root, boolean applyingAsChild) {
        switch (name) {
            case "currentRebootGeneration" :
                return node.withCurrentRebootGeneration(asLong(value), clock.instant());
            case "currentRestartGeneration" :
                return patchCurrentRestartGeneration(node, asLong(value));
            case "currentDockerImage" :
                if (node.type().isHost())
                    throw new IllegalArgumentException("Container image can only be set for child nodes");
                return node.with(node.status().withContainerImage(DockerImage.fromString(asString(value))));
            case "vespaVersion" :
            case "currentVespaVersion" :
                return node.with(node.status().withVespaVersion(Version.fromString(asString(value))));
            case "currentOsVersion" :
                return node.withCurrentOsVersion(Version.fromString(asString(value)), clock.instant());
            case "currentFirmwareCheck":
                return node.withFirmwareVerifiedAt(Instant.ofEpochMilli(asLong(value)));
            case "failCount" :
                return node.with(node.status().withFailCount(asLong(value).intValue()));
            case "flavor" :
                return node.with(nodeFlavors.getFlavorOrThrow(asString(value)));
            case "parentHostname" :
                return node.withParentHostname(asString(value));
            case "ipAddresses" :
                return IP.Config.verify(node.with(node.ipConfig().withPrimary(asStringSet(value))), memoizedNodes.get());
            case "additionalIpAddresses" :
                return IP.Config.verify(node.with(node.ipConfig().withPool(node.ipConfig().pool().withIpAddresses(asStringSet(value)))), memoizedNodes.get());
            case "additionalHostnames" :
                return IP.Config.verify(node.with(node.ipConfig().withPool(node.ipConfig().pool().withAddresses(asAddressList(value)))), memoizedNodes.get());
            case WANT_TO_RETIRE :
            case WANT_TO_DEPROVISION :
                boolean wantToRetire = asOptionalBoolean(root.field(WANT_TO_RETIRE)).orElse(node.status().wantToRetire());
                boolean wantToDeprovision = asOptionalBoolean(root.field(WANT_TO_DEPROVISION)).orElse(node.status().wantToDeprovision());
                return node.withWantToRetire(wantToRetire, wantToDeprovision && !applyingAsChild, Agent.operator, clock.instant());
            case "reports" :
                return nodeWithPatchedReports(node, value);
            case "openStackId" :
                return node.withOpenStackId(asString(value));
            case "diskGb":
            case "minDiskAvailableGb":
                return node.with(node.flavor().with(node.flavor().resources().withDiskGb(value.asDouble())));
            case "memoryGb":
            case "minMainMemoryAvailableGb":
                return node.with(node.flavor().with(node.flavor().resources().withMemoryGb(value.asDouble())));
            case "vcpu":
            case "minCpuCores":
                return node.with(node.flavor().with(node.flavor().resources().withVcpu(value.asDouble())));
            case "fastDisk":
                return node.with(node.flavor().with(node.flavor().resources().with(value.asBool() ? fast : slow)));
            case "remoteStorage":
                return node.with(node.flavor().with(node.flavor().resources().with(value.asBool() ? remote : local)));
            case "bandwidthGbps":
                return node.with(node.flavor().with(node.flavor().resources().withBandwidthGbps(value.asDouble())));
            case "modelName":
                return value.type() == Type.NIX ? node.withoutModelName() : node.withModelName(asString(value));
            case "requiredDiskSpeed":
                return patchRequiredDiskSpeed(node, asString(value));
            case "reservedTo":
                return value.type() == Type.NIX ? node.withoutReservedTo() : node.withReservedTo(TenantName.from(value.asString()));
            case "exclusiveTo":
                return node.withExclusiveTo(SlimeUtils.optionalString(value).map(ApplicationId::fromSerializedForm).orElse(null));
            case "switchHostname":
                return value.type() == Type.NIX ? node.withoutSwitchHostname() : node.withSwitchHostname(value.asString());
            default :
                throw new IllegalArgumentException("Could not apply field '" + name + "' on a node: No such modifiable field");
        }
    }

    private Node nodeWithPatchedReports(Node node, Inspector reportsInspector) {
        Node patchedNode;
        // "reports": null clears the reports
        if (reportsInspector.type() == Type.NIX) {
            patchedNode = node.with(new Reports());
        } else {
            var reportsBuilder = new Reports.Builder(node.reports());
            reportsInspector.traverse((ObjectTraverser) (reportId, reportInspector) -> {
                if (reportInspector.type() == Type.NIX) {
                    // ... "reports": { "reportId": null } clears the report "reportId"
                    reportsBuilder.clearReport(reportId);
                } else {
                    // ... "reports": { "reportId": {...} } overrides the whole report "reportId"
                    reportsBuilder.setReport(Report.fromSlime(reportId, reportInspector));
                }
            });
            patchedNode = node.with(reportsBuilder.build());
        }

        boolean hadHardFailReports = node.reports().getReports().stream()
                .anyMatch(r -> r.getType() == Report.Type.HARD_FAIL);
        boolean hasHardFailReports = patchedNode.reports().getReports().stream()
                .anyMatch(r -> r.getType() == Report.Type.HARD_FAIL);

        // If this patch resulted in going from not having HARD_FAIL report to having one, or vice versa
        if (hadHardFailReports != hasHardFailReports) {
            // Do not automatically change wantToDeprovision when
            // 1. Transitioning to having a HARD_FAIL report and being in state failed:
            //    To allow operators manually unset before the host is parked and deleted.
            // 2. When in parked state: Deletion is imminent, possibly already underway
            if ((hasHardFailReports && node.state() == Node.State.failed) || node.state() == Node.State.parked)
                return patchedNode;

            patchedNode = patchedNode.withWantToRetire(hasHardFailReports, hasHardFailReports, Agent.system, clock.instant());
        }

        return patchedNode;
    }

    private Set<String> asStringSet(Inspector field) {
        if ( ! field.type().equals(Type.ARRAY))
            throw new IllegalArgumentException("Expected an ARRAY value, got a " + field.type());

        TreeSet<String> strings = new TreeSet<>();
        for (int i = 0; i < field.entries(); i++) {
            Inspector entry = field.entry(i);
            if ( ! entry.type().equals(Type.STRING))
                throw new IllegalArgumentException("Expected a STRING value, got a " + entry.type());
            strings.add(entry.asString());
        }

        return strings;
    }

    private List<Address> asAddressList(Inspector field) {
        if ( ! field.type().equals(Type.ARRAY))
            throw new IllegalArgumentException("Expected an ARRAY value, got a " + field.type());

        List<Address> addresses = new ArrayList<>(field.entries());
        for (int i = 0; i < field.entries(); i++) {
            Inspector entry = field.entry(i);
            if ( ! entry.type().equals(Type.STRING))
                throw new IllegalArgumentException("Expected a STRING value, got a " + entry.type());
            Address address = new Address(entry.asString());
            addresses.add(address);
        }

        return addresses;
    }

    private Node patchRequiredDiskSpeed(Node node, String value) {
        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent())
            return node.with(allocation.get().withRequestedResources(
                    allocation.get().requestedResources().with(NodeResources.DiskSpeed.valueOf(value))));
        else
            throw new IllegalArgumentException("Node is not allocated");
    }
    
    private Node patchCurrentRestartGeneration(Node node, Long value) {
        Optional<Allocation> allocation = node.allocation();
        if (allocation.isPresent())
            return node.with(allocation.get().withRestart(allocation.get().restartGeneration().withCurrent(value)));
        else
            throw new IllegalArgumentException("Node is not allocated");
    }

    private Long asLong(Inspector field) {
        if ( ! field.type().equals(Type.LONG))
            throw new IllegalArgumentException("Expected a LONG value, got a " + field.type());
        return field.asLong();
    }

    private String asString(Inspector field) {
        if ( ! field.type().equals(Type.STRING))
            throw new IllegalArgumentException("Expected a STRING value, got a " + field.type());
        return field.asString();
    }

    private boolean asBoolean(Inspector field) {
        if ( ! field.type().equals(Type.BOOL))
            throw new IllegalArgumentException("Expected a BOOL value, got a " + field.type());
        return field.asBool();
    }

    private Optional<Boolean> asOptionalBoolean(Inspector field) {
        return Optional.of(field).filter(Inspector::valid).map(this::asBoolean);
    }

    private class PatchedNodes implements Mutex {
        private final Map<String, NodeMutex> nodes = new HashMap<>();
        private final String hostname;
        private boolean fetchedChildren;

        private PatchedNodes(NodeMutex nodeMutex) {
            this.hostname = nodeMutex.node().hostname();
            nodes.put(hostname, nodeMutex);
            fetchedChildren = !nodeMutex.node().type().isHost();
        }

        public NodeMutex nodeMutex() {
            return nodes.get(hostname);
        }

        public Node node() {
            return nodeMutex().node();
        }

        public List<Node> children() {
            if (!fetchedChildren) {
                memoizedNodes.get()
                        .childrenOf(hostname)
                        .forEach(node -> nodeRepository.nodes().lockAndGet(node)
                                .ifPresent(nodeMutex -> nodes.put(nodeMutex.node().hostname(), nodeMutex)));
                fetchedChildren = true;
            }
            return nodes.values().stream()
                    .map(NodeMutex::node)
                    .filter(node -> !node.type().isHost())
                    .collect(Collectors.toList());
        }

        public void update(Node node) {
            NodeMutex currentNodeMutex = nodes.get(node.hostname());
            if (currentNodeMutex == null) {
                throw new IllegalStateException("unable to update non-existing child: " + node.hostname());
            }

            nodes.put(node.hostname(), currentNodeMutex.with(node));
        }

        public List<Node> nodes() {
            return nodes.values().stream().map(NodeMutex::node).collect(Collectors.toList());
        }

        @Override
        public void close() {
            nodes.values().forEach(NodeMutex::close);
        }
    }
}
