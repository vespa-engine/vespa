// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.restapi;

import com.google.common.collect.Maps;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.WireguardKey;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.slime.Type;
import com.yahoo.vespa.hosted.provision.LockedNodeList;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.NodeList;
import com.yahoo.vespa.hosted.provision.NodeMutex;
import com.yahoo.vespa.hosted.provision.NodeRepository;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Report;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.TrustStoreItem;
import com.yahoo.yolean.Exceptions;

import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

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
public class NodePatcher {

    private static final String WANT_TO_RETIRE = "wantToRetire";
    private static final String WANT_TO_DEPROVISION = "wantToDeprovision";
    private static final String WANT_TO_REBUILD = "wantToRebuild";
    private static final Set<String> RECURSIVE_FIELDS = Set.of(WANT_TO_RETIRE, WANT_TO_DEPROVISION);
    private static final Set<String> IP_CONFIG_FIELDS = Set.of("ipAddresses",
                                                               "additionalIpAddresses",
                                                               "additionalHostnames");

    private final NodeRepository nodeRepository;
    private final NodeFlavors nodeFlavors;
    private final Clock clock;

    public NodePatcher(NodeFlavors nodeFlavors, NodeRepository nodeRepository) {
        this.nodeRepository = nodeRepository;
        this.nodeFlavors = nodeFlavors;
        this.clock = nodeRepository.clock();
    }

    /**
     * Apply given JSON to the node identified by hostname. Any patched node(s) are written to the node repository.
     *
     * Note: This may patch more than one node if the field being patched must be applied recursively to host and node.
     */
    public void patch(String hostname, InputStream json) {
        unifiedPatch(hostname, json, false);
    }

    /** Apply given JSON from a tenant host that may have been compromised. */
    public void patchFromUntrustedTenantHost(String hostname, InputStream json) {
        unifiedPatch(hostname, json, true);
    }

    private void unifiedPatch(String hostname, InputStream json, boolean untrustedTenantHost) {
        Inspector root = Exceptions.uncheck(() -> SlimeUtils.jsonToSlime(json.readAllBytes())).get();
        Map<String, Inspector> fields = new HashMap<>();
        root.traverse(fields::put);

        if (untrustedTenantHost) {
            var disallowedFields = new HashSet<>(fields.keySet());
            disallowedFields.removeAll(Set.of("currentDockerImage",
                                              "currentFirmwareCheck",
                                              "currentOsVersion",
                                              "currentRebootGeneration",
                                              "currentRestartGeneration",
                                              "reports",
                                              "trustStore",
                                              "vespaVersion",
                                              "wireguardPubkey"));
            if (!disallowedFields.isEmpty()) {
                throw new IllegalArgumentException("Patching fields not supported: " + disallowedFields);
            }
        }

        // Create views grouping fields by their locking requirements
        Map<String, Inspector> regularFields = Maps.filterKeys(fields, k -> !IP_CONFIG_FIELDS.contains(k));
        Map<String, Inspector> ipConfigFields = Maps.filterKeys(fields, IP_CONFIG_FIELDS::contains);
        Map<String, Inspector> recursiveFields = Maps.filterKeys(fields, RECURSIVE_FIELDS::contains);

        // Patch
        NodeMutex nodeMutex = nodeRepository.nodes().lockAndGetRequired(hostname, Duration.ofSeconds(10)); // timeout should match the one used by clients
        patch(nodeMutex, regularFields, root, false);
        patchIpConfig(hostname, ipConfigFields);
        if (nodeMutex.node().type().isHost()) {
            patchChildrenOf(hostname, recursiveFields, root);
        }
    }

    private void patch(NodeMutex nodeMutex, Map<String, Inspector> fields, Inspector root, boolean applyingAsChild) {
        try (var lock = nodeMutex) {
            Node node = nodeMutex.node();
            for (var kv : fields.entrySet()) {
                String name = kv.getKey();
                Inspector value = kv.getValue();
                try {
                    node = applyField(node, name, value, root, applyingAsChild);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Could not set field '" + name + "'", e);
                }
            }
            nodeRepository.nodes().write(node, lock);
        }
    }

    private void patchIpConfig(String hostname, Map<String, Inspector> ipConfigFields) {
        if (ipConfigFields.isEmpty()) return; // Nothing to patch
        try (var allocationLock = nodeRepository.nodes().lockUnallocated()) {
            LockedNodeList nodes = nodeRepository.nodes().list(allocationLock);
            Node node = nodes.node(hostname).orElseThrow(() -> new NotFoundException("No node with hostname '" + hostname + "'"));
            for (var kv : ipConfigFields.entrySet()) {
                String name = kv.getKey();
                Inspector value = kv.getValue();
                try {
                    node = applyIpconfigField(node, name, value, nodes);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("Could not set field '" + name + "'", e);
                }
            }
            nodeRepository.nodes().write(node, allocationLock);
        }
    }

    private void patchChildrenOf(String hostname, Map<String, Inspector> recursiveFields, Inspector root) {
        if (recursiveFields.isEmpty()) return;
        NodeList children = nodeRepository.nodes().list().childrenOf(hostname);
        for (var child : children) {
            Optional<NodeMutex> childNodeMutex = nodeRepository.nodes().lockAndGet(child.hostname());
            if (childNodeMutex.isEmpty()) continue;  // Node disappeared after locking
            patch(childNodeMutex.get(), recursiveFields, root, true);
        }
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
            case "wantedOsVersion": // Node repository manages this field internally. Setting this manually should only be used for debugging purposes
                return node.withWantedOsVersion(value.type() == Type.NIX ? Optional.empty() : Optional.of(Version.fromString(value.asString())));
            case "currentFirmwareCheck":
                return node.withFirmwareVerifiedAt(Instant.ofEpochMilli(asLong(value)));
            case "failCount" :
                return node.with(node.status().withFailCount(asLong(value).intValue()));
            case "flavor" :
                return node.with(nodeFlavors.getFlavorOrThrow(asString(value)), Agent.operator, clock.instant());
            case "parentHostname" :
                return node.withParentHostname(asString(value));
            case WANT_TO_RETIRE:
            case WANT_TO_DEPROVISION:
            case WANT_TO_REBUILD:
                // These needs to be handled as one, because certain combinations are not allowed.
                return node.withWantToRetire(asOptionalBoolean(root.field(WANT_TO_RETIRE)).orElseGet(node.status()::wantToRetire),
                                             asOptionalBoolean(root.field(WANT_TO_DEPROVISION))
                                                     .orElseGet(node.status()::wantToDeprovision),
                                             asOptionalBoolean(root.field(WANT_TO_REBUILD))
                                                     .filter(want -> !applyingAsChild)
                                                     .orElseGet(node.status()::wantToRebuild),
                                             Agent.operator,
                                             clock.instant());
            case "reports" :
                return nodeWithPatchedReports(node, value);
            case "id" :
                return node.withId(asString(value));
            case "diskGb":
            case "minDiskAvailableGb":
                return node.with(node.flavor().with(node.flavor().resources().withDiskGb(value.asDouble())), Agent.operator, clock.instant());
            case "memoryGb":
            case "minMainMemoryAvailableGb":
                return node.with(node.flavor().with(node.flavor().resources().withMemoryGb(value.asDouble())), Agent.operator, clock.instant());
            case "vcpu":
            case "minCpuCores":
                return node.with(node.flavor().with(node.flavor().resources().withVcpu(value.asDouble())), Agent.operator, clock.instant());
            case "fastDisk":
                return node.with(node.flavor().with(node.flavor().resources().with(value.asBool() ? fast : slow)), Agent.operator, clock.instant());
            case "remoteStorage":
                return node.with(node.flavor().with(node.flavor().resources().with(value.asBool() ? remote : local)), Agent.operator, clock.instant());
            case "bandwidthGbps":
                return node.with(node.flavor().with(node.flavor().resources().withBandwidthGbps(value.asDouble())), Agent.operator, clock.instant());
            case "modelName":
                return value.type() == Type.NIX ? node.withoutModelName() : node.withModelName(asString(value));
            case "requiredDiskSpeed":
                return patchRequiredDiskSpeed(node, asString(value));
            case "reservedTo":
                return value.type() == Type.NIX ? node.withoutReservedTo() : node.withReservedTo(TenantName.from(value.asString()));
            case "exclusiveTo":
            case "exclusiveToApplicationId":
                return node.withExclusiveToApplicationId(SlimeUtils.optionalString(value).map(ApplicationId::fromSerializedForm).orElse(null));
            case "exclusiveToClusterType":
                return node.withExclusiveToClusterType(SlimeUtils.optionalString(value).map(ClusterSpec.Type::valueOf).orElse(null));
            case "switchHostname":
                return value.type() == Type.NIX ? node.withoutSwitchHostname() : node.withSwitchHostname(value.asString());
            case "trustStore":
                return nodeWithTrustStore(node, value);
            case "wireguardPubkey":
                return node.withWireguardPubkey(SlimeUtils.optionalString(value).map(WireguardKey::new).orElse(null));
            default :
                throw new IllegalArgumentException("Could not apply field '" + name + "' on a node: No such modifiable field");
        }
    }

    private Node applyIpconfigField(Node node, String name, Inspector value, LockedNodeList nodes) {
        switch (name) {
            case "ipAddresses" -> {
                return IP.Config.verify(node.with(node.ipConfig().withPrimary(asStringSet(value))), nodes);
            }
            case "additionalIpAddresses" -> {
                return IP.Config.verify(node.with(node.ipConfig().withPool(node.ipConfig().pool().withIpAddresses(asStringSet(value)))), nodes);
            }
            case "additionalHostnames" -> {
                return IP.Config.verify(node.with(node.ipConfig().withPool(node.ipConfig().pool().withHostnames(asHostnames(value)))), nodes);
            }
        }
        throw new IllegalArgumentException("Could not apply field '" + name + "' on a node: No such modifiable field");
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

    private Node nodeWithTrustStore(Node node, Inspector inspector) {
        List<TrustStoreItem> trustStoreItems =
                SlimeUtils.entriesStream(inspector)
                        .map(TrustStoreItem::fromSlime)
                        .toList();
        return node.with(trustStoreItems);
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

    private List<HostName> asHostnames(Inspector field) {
        if ( ! field.type().equals(Type.ARRAY))
            throw new IllegalArgumentException("Expected an ARRAY value, got a " + field.type());

        List<HostName> hostnames = new ArrayList<>(field.entries());
        for (int i = 0; i < field.entries(); i++) {
            Inspector entry = field.entry(i);
            if ( ! entry.type().equals(Type.STRING))
                throw new IllegalArgumentException("Expected a STRING value, got a " + entry.type());
            hostnames.add(HostName.of(entry.asString()));
        }

        return hostnames;
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

}
