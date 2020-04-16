// Copyright 2018 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.config.provision.host.FlavorOverrides;
import com.yahoo.config.provision.serialization.NetworkPortsSerializer;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.slime.Type;
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.node.OsVersion;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

/**
 * Serializes a node to/from JSON.
 * Instances of this are multithread safe and can be reused
 *
 * @author bratseth
 */
public class NodeSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    /** The configured node flavors */
    private final NodeFlavors flavors;

    // Node fields
    private static final String hostnameKey = "hostname";
    private static final String ipAddressesKey = "ipAddresses";
    private static final String ipAddressPoolKey = "additionalIpAddresses";
    private static final String idKey = "openStackId";
    private static final String parentHostnameKey = "parentHostname";
    private static final String historyKey = "history";
    private static final String instanceKey = "instance"; // legacy name, TODO: change to allocation with backwards compat
    private static final String rebootGenerationKey = "rebootGeneration";
    private static final String currentRebootGenerationKey = "currentRebootGeneration";
    private static final String vespaVersionKey = "vespaVersion";
    private static final String currentDockerImageKey = "currentDockerImage";
    private static final String failCountKey = "failCount";
    private static final String nodeTypeKey = "type";
    private static final String wantToRetireKey = "wantToRetire";
    private static final String wantToDeprovisionKey = "wantToDeprovision";
    private static final String osVersionKey = "osVersion";
    private static final String wantedOsVersionKey = "wantedOsVersion";
    private static final String firmwareCheckKey = "firmwareCheck";
    private static final String reportsKey = "reports";
    private static final String modelNameKey = "modelName";
    private static final String reservedToKey = "reservedTo";

    // Node resource fields
    // ...for hosts and nodes allocated by legacy flavor specs
    private static final String flavorKey = "flavor";
    // ...for nodes allocated by resources
    private static final String resourcesKey = "resources";
    private static final String vcpuKey = "vcpu";
    private static final String memoryKey = "memory";
    private static final String diskKey = "disk";
    private static final String bandwidthKey = "bandwidth";
    private static final String diskSpeedKey = "diskSpeed";
    private static final String storageTypeKey = "storageType";

    // Allocation fields
    private static final String tenantIdKey = "tenantId";
    private static final String applicationIdKey = "applicationId";
    private static final String instanceIdKey = "instanceId";
    private static final String serviceIdKey = "serviceId"; // legacy name, TODO: change to membership with backwards compat
    private static final String requestedResourcesKey = "requestedResources";
    private static final String restartGenerationKey = "restartGeneration";
    private static final String currentRestartGenerationKey = "currentRestartGeneration";
    private static final String removableKey = "removable";
    // Saved as part of allocation instead of serviceId, since serviceId serialized form is not easily extendable.
    private static final String wantedVespaVersionKey = "wantedVespaVersion";
    private static final String wantedDockerImageRepoKey = "wantedDockerImageRepo";

    // History event fields
    private static final String historyEventTypeKey = "type";
    private static final String atKey = "at";
    private static final String agentKey = "agent"; // retired events only

    // Network port fields
    private static final String networkPortsKey = "networkPorts";

    // ---------------- Serialization ----------------------------------------------------

    public NodeSerializer(NodeFlavors flavors) {
        this.flavors = flavors;
    }

    public byte[] toJson(Node node) {
        try {
            Slime slime = new Slime();
            toSlime(node, slime.setObject());
            return SlimeUtils.toJsonBytes(slime);
        }
        catch (IOException e) {
            throw new RuntimeException("Serialization of " + node + " to json failed", e);
        }
    }

    private void toSlime(Node node, Cursor object) {
        object.setString(hostnameKey, node.hostname());
        toSlime(node.ipConfig().primary(), object.setArray(ipAddressesKey), IP.Config::require);
        toSlime(node.ipConfig().pool().asSet(), object.setArray(ipAddressPoolKey), UnaryOperator.identity() /* Pool already holds a validated address list */);
        object.setString(idKey, node.id());
        node.parentHostname().ifPresent(hostname -> object.setString(parentHostnameKey, hostname));
        toSlime(node.flavor(), object);
        object.setLong(rebootGenerationKey, node.status().reboot().wanted());
        object.setLong(currentRebootGenerationKey, node.status().reboot().current());
        node.status().vespaVersion().ifPresent(version -> object.setString(vespaVersionKey, version.toString()));
        node.status().dockerImage().ifPresent(image -> object.setString(currentDockerImageKey, image.asString()));
        object.setLong(failCountKey, node.status().failCount());
        object.setBool(wantToRetireKey, node.status().wantToRetire());
        object.setBool(wantToDeprovisionKey, node.status().wantToDeprovision());
        node.allocation().ifPresent(allocation -> toSlime(allocation, object.setObject(instanceKey)));
        toSlime(node.history(), object.setArray(historyKey));
        object.setString(nodeTypeKey, toString(node.type()));
        node.status().osVersion().current().ifPresent(version -> object.setString(osVersionKey, version.toString()));
        node.status().osVersion().wanted().ifPresent(version -> object.setString(wantedOsVersionKey, version.toFullString()));
        node.status().firmwareVerifiedAt().ifPresent(instant -> object.setLong(firmwareCheckKey, instant.toEpochMilli()));
        node.reports().toSlime(object, reportsKey);
        node.modelName().ifPresent(modelName -> object.setString(modelNameKey, modelName));
        node.reservedTo().ifPresent(tenant -> object.setString(reservedToKey, tenant.value()));
    }

    private void toSlime(Flavor flavor, Cursor object) {
        if (flavor.isConfigured()) {
            object.setString(flavorKey, flavor.name());
            if (flavor.flavorOverrides().isPresent()) {
                Cursor resourcesObject = object.setObject(resourcesKey);
                flavor.flavorOverrides().get().diskGb().ifPresent(diskGb -> resourcesObject.setDouble(diskKey, diskGb));
            }
        }
        else {
            toSlime(flavor.resources(), object.setObject(resourcesKey));
        }
    }

    private void toSlime(NodeResources resources, Cursor resourcesObject) {
        resourcesObject.setDouble(vcpuKey, resources.vcpu());
        resourcesObject.setDouble(memoryKey, resources.memoryGb());
        resourcesObject.setDouble(diskKey, resources.diskGb());
        resourcesObject.setDouble(bandwidthKey, resources.bandwidthGbps());
        resourcesObject.setString(diskSpeedKey, diskSpeedToString(resources.diskSpeed()));
        resourcesObject.setString(storageTypeKey, storageTypeToString(resources.storageType()));
    }

    private void toSlime(Allocation allocation, Cursor object) {
        toSlime(allocation.requestedResources(), object.setObject(requestedResourcesKey));
        object.setString(tenantIdKey, allocation.owner().tenant().value());
        object.setString(applicationIdKey, allocation.owner().application().value());
        object.setString(instanceIdKey, allocation.owner().instance().value());
        object.setString(serviceIdKey, allocation.membership().stringValue());
        object.setLong(restartGenerationKey, allocation.restartGeneration().wanted());
        object.setLong(currentRestartGenerationKey, allocation.restartGeneration().current());
        object.setBool(removableKey, allocation.isRemovable());
        object.setString(wantedVespaVersionKey, allocation.membership().cluster().vespaVersion().toString());
        allocation.membership().cluster().dockerImageRepo().ifPresent(repo -> object.setString(wantedDockerImageRepoKey, repo));
        allocation.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray(networkPortsKey)));
    }

    private void toSlime(History history, Cursor array) {
        for (History.Event event : history.events())
            toSlime(event, array.addObject());
    }

    private void toSlime(History.Event event, Cursor object) {
        object.setString(historyEventTypeKey, toString(event.type()));
        object.setLong(atKey, event.at().toEpochMilli());
        object.setString(agentKey, toString(event.agent()));
    }

    private void toSlime(Set<String> ipAddresses, Cursor array, UnaryOperator<Set<String>> validator) {
        // Sorting IP addresses is expensive, so we do it at serialization time instead of Node construction time
        validator.apply(ipAddresses).stream().sorted(IP.NATURAL_ORDER).forEach(array::addString);
    }

    // ---------------- Deserialization --------------------------------------------------

    public Node fromJson(Node.State state, byte[] data) {
        return nodeFromSlime(state, SlimeUtils.jsonToSlime(data).get());
    }

    private Node nodeFromSlime(Node.State state, Inspector object) {
        Flavor flavor = flavorFromSlime(object);
        return new Node(object.field(idKey).asString(),
                        new IP.Config(ipAddressesFromSlime(object, ipAddressesKey),
                                      ipAddressesFromSlime(object, ipAddressPoolKey)),
                        object.field(hostnameKey).asString(),
                        parentHostnameFromSlime(object),
                        flavor,
                        statusFromSlime(object),
                        state,
                        allocationFromSlime(flavor.resources(), object.field(instanceKey)),
                        historyFromSlime(object.field(historyKey)),
                        nodeTypeFromString(object.field(nodeTypeKey).asString()),
                        Reports.fromSlime(object.field(reportsKey)),
                        modelNameFromSlime(object),
                        reservedToFromSlime(object.field(reservedToKey)));
    }

    private Status statusFromSlime(Inspector object) {
        return new Status(generationFromSlime(object, rebootGenerationKey, currentRebootGenerationKey),
                          versionFromSlime(object.field(vespaVersionKey)),
                          dockerImageFromSlime(object.field(currentDockerImageKey)),
                          (int) object.field(failCountKey).asLong(),
                          object.field(wantToRetireKey).asBool(),
                          object.field(wantToDeprovisionKey).asBool(),
                          new OsVersion(versionFromSlime(object.field(osVersionKey)),
                                        versionFromSlime(object.field(wantedOsVersionKey))),
                          instantFromSlime(object.field(firmwareCheckKey)));
    }

    private Flavor flavorFromSlime(Inspector object) {
        Inspector resources = object.field(resourcesKey);

        if (object.field(flavorKey).valid()) {
            Flavor flavor = flavors.getFlavorOrThrow(object.field(flavorKey).asString());
            if (!resources.valid()) return flavor;
            return flavor.with(FlavorOverrides.ofDisk(resources.field(diskKey).asDouble()));
        }
        else {
            return new Flavor(resourcesFromSlime(resources).get());
        }
    }

    private Optional<NodeResources> resourcesFromSlime(Inspector resources) {
        if ( ! resources.valid()) return Optional.empty();

        return Optional.of(new NodeResources(resources.field(vcpuKey).asDouble(),
                                             resources.field(memoryKey).asDouble(),
                                             resources.field(diskKey).asDouble(),
                                             resources.field(bandwidthKey).asDouble(),
                                             diskSpeedFromSlime(resources.field(diskSpeedKey)),
                                             storageTypeFromSlime(resources.field(storageTypeKey))));
    }

    private Optional<Allocation> allocationFromSlime(NodeResources assignedResources, Inspector object) {
        if ( ! object.valid()) return Optional.empty(); // TODO: Remove this line (and to the simplifications that follows) after November 2019
        return Optional.of(new Allocation(applicationIdFromSlime(object),
                                          clusterMembershipFromSlime(object),
                                          resourcesFromSlime(object.field(requestedResourcesKey)).orElse(assignedResources),
                                          generationFromSlime(object, restartGenerationKey, currentRestartGenerationKey),
                                          object.field(removableKey).asBool(),
                                          NetworkPortsSerializer.fromSlime(object.field(networkPortsKey))));
    }

    private ApplicationId applicationIdFromSlime(Inspector object) {
        return ApplicationId.from(TenantName.from(object.field(tenantIdKey).asString()),
                                  ApplicationName.from(object.field(applicationIdKey).asString()),
                                  InstanceName.from(object.field(instanceIdKey).asString()));
    }

    private History historyFromSlime(Inspector array) {
        List<History.Event> events = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> {
            History.Event event = eventFromSlime(item);
            if (event != null)
                events.add(event);
        });
        return new History(events);
    }

    private History.Event eventFromSlime(Inspector object) {
        History.Event.Type type = eventTypeFromString(object.field(historyEventTypeKey).asString());
        if (type == null) return null;
        Instant at = Instant.ofEpochMilli(object.field(atKey).asLong());
        Agent agent = eventAgentFromSlime(object.field(agentKey));
        return new History.Event(type, agent, at);
    }

    private Generation generationFromSlime(Inspector object, String wantedField, String currentField) {
        Inspector current = object.field(currentField);
        return new Generation(object.field(wantedField).asLong(), current.asLong());
    }

    private ClusterMembership clusterMembershipFromSlime(Inspector object) {
        return ClusterMembership.from(object.field(serviceIdKey).asString(), 
                                      versionFromSlime(object.field(wantedVespaVersionKey)).get(),
                                      dockerImageRepoFromSlime(object.field(wantedDockerImageRepoKey)));
    }

    private Optional<Version> versionFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(Version.fromString(object.asString()));
    }

    private Optional<String> dockerImageRepoFromSlime(Inspector object) {
        if ( ! object.valid() || object.asString().isEmpty()) return Optional.empty();
        return Optional.of(object.asString());
    }

    private Optional<DockerImage> dockerImageFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(DockerImage.fromString(object.asString()));
    }

    private Optional<Instant> instantFromSlime(Inspector object) {
        if ( ! object.valid())
            return Optional.empty();
        return Optional.of(Instant.ofEpochMilli(object.asLong()));
    }

    private Optional<String> parentHostnameFromSlime(Inspector object) {
        if (object.field(parentHostnameKey).valid())
            return Optional.of(object.field(parentHostnameKey).asString());
        else
            return Optional.empty();
    }

    private Set<String> ipAddressesFromSlime(Inspector object, String key) {
        ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
        object.field(key).traverse((ArrayTraverser) (i, item) -> ipAddresses.add(item.asString()));
        return ipAddresses.build();
    }

    private Optional<String> modelNameFromSlime(Inspector object) {
        if (object.field(modelNameKey).valid()) {
            return Optional.of(object.field(modelNameKey).asString());
        }
        return Optional.empty();
    }

    private Optional<TenantName> reservedToFromSlime(Inspector object) {
        if (! object.valid()) return Optional.empty();
        if (object.type() != Type.STRING)
            throw new IllegalArgumentException("Expected 'reservedTo' to be a string but is " + object);
        return Optional.of(TenantName.from(object.asString()));
    }

    // ----------------- Enum <-> string mappings ----------------------------------------
    
    /** Returns the event type, or null if this event type should be ignored */
    private History.Event.Type eventTypeFromString(String eventTypeString) {
        switch (eventTypeString) {
            case "provisioned" : return History.Event.Type.provisioned;
            case "deprovisioned" : return History.Event.Type.deprovisioned;
            case "readied" : return History.Event.Type.readied;
            case "reserved" : return History.Event.Type.reserved;
            case "activated" : return History.Event.Type.activated;
            case "wantToRetire": return History.Event.Type.wantToRetire;
            case "retired" : return History.Event.Type.retired;
            case "deactivated" : return History.Event.Type.deactivated;
            case "parked" : return History.Event.Type.parked;
            case "failed" : return History.Event.Type.failed;
            case "deallocated" : return History.Event.Type.deallocated;
            case "down" : return History.Event.Type.down;
            case "requested" : return History.Event.Type.requested;
            case "rebooted" : return History.Event.Type.rebooted;
            case "osUpgraded" : return History.Event.Type.osUpgraded;
            case "firmwareVerified" : return History.Event.Type.firmwareVerified;
        }
        throw new IllegalArgumentException("Unknown node event type '" + eventTypeString + "'");
    }

    private String toString(History.Event.Type nodeEventType) {
        switch (nodeEventType) {
            case provisioned : return "provisioned";
            case deprovisioned : return "deprovisioned";
            case readied : return "readied";
            case reserved : return "reserved";
            case activated : return "activated";
            case wantToRetire: return "wantToRetire";
            case retired : return "retired";
            case deactivated : return "deactivated";
            case parked : return "parked";
            case failed : return "failed";
            case deallocated : return "deallocated";
            case down : return "down";
            case requested: return "requested";
            case rebooted: return "rebooted";
            case osUpgraded: return "osUpgraded";
            case firmwareVerified: return "firmwareVerified";
        }
        throw new IllegalArgumentException("Serialized form of '" + nodeEventType + "' not defined");
    }

    private Agent eventAgentFromSlime(Inspector eventAgentField) {
        switch (eventAgentField.asString()) {
            case "operator" : return Agent.operator;
            case "application" : return Agent.application;
            case "system" : return Agent.system;
            case "NodeFailer" : return Agent.NodeFailer;
            case "Rebalancer" : return Agent.Rebalancer;
            case "DirtyExpirer" : return Agent.DirtyExpirer;
            case "FailedExpirer" : return Agent.FailedExpirer;
            case "InactiveExpirer" : return Agent.InactiveExpirer;
            case "ProvisionedExpirer" : return Agent.ProvisionedExpirer;
            case "ReservationExpirer" : return Agent.ReservationExpirer;
            case "DynamicProvisioningMaintainer" : return Agent.DynamicProvisioningMaintainer;
        }
        throw new IllegalArgumentException("Unknown node event agent '" + eventAgentField.asString() + "'");
    }
    private String toString(Agent agent) {
        switch (agent) {
            case operator : return "operator";
            case application : return "application";
            case system : return "system";
            case NodeFailer : return "NodeFailer";
            case Rebalancer : return "Rebalancer";
            case DirtyExpirer : return "DirtyExpirer";
            case FailedExpirer : return "FailedExpirer";
            case InactiveExpirer : return "InactiveExpirer";
            case ProvisionedExpirer : return "ProvisionedExpirer";
            case ReservationExpirer : return "ReservationExpirer";
            case DynamicProvisioningMaintainer : return "DynamicProvisioningMaintainer";
        }
        throw new IllegalArgumentException("Serialized form of '" + agent + "' not defined");
    }

    static NodeType nodeTypeFromString(String typeString) {
        switch (typeString) {
            case "tenant": return NodeType.tenant;
            case "host": return NodeType.host;
            case "proxy": return NodeType.proxy;
            case "proxyhost": return NodeType.proxyhost;
            case "config": return NodeType.config;
            case "confighost": return NodeType.confighost;
            case "controller": return NodeType.controller;
            case "controllerhost": return NodeType.controllerhost;
            case "devhost": return NodeType.devhost;
            default : throw new IllegalArgumentException("Unknown node type '" + typeString + "'");
        }
    }

    static String toString(NodeType type) {
        switch (type) {
            case tenant: return "tenant";
            case host: return "host";
            case proxy: return "proxy";
            case proxyhost: return "proxyhost";
            case config: return "config";
            case confighost: return "confighost";
            case controller: return "controller";
            case controllerhost: return "controllerhost";
            case devhost: return "devhost";
        }
        throw new IllegalArgumentException("Serialized form of '" + type + "' not defined");
    }

    private static NodeResources.DiskSpeed diskSpeedFromSlime(Inspector diskSpeed) {
        switch (diskSpeed.asString()) {
            case "fast" : return NodeResources.DiskSpeed.fast;
            case "slow" : return NodeResources.DiskSpeed.slow;
            case "any" : return NodeResources.DiskSpeed.any;
            default: throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed.asString() + "'");
        }
    }

    private static String diskSpeedToString(NodeResources.DiskSpeed diskSpeed) {
        switch (diskSpeed) {
            case fast : return "fast";
            case slow : return "slow";
            case any : return "any";
            default: throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed + "'");
        }
    }

    private static NodeResources.StorageType storageTypeFromSlime(Inspector storageType) {
        if ( ! storageType.valid()) return NodeResources.StorageType.getDefault(); // TODO: Remove this line after December 2019
        switch (storageType.asString()) {
            case "remote" : return NodeResources.StorageType.remote;
            case "local" : return NodeResources.StorageType.local;
            case "any" : return NodeResources.StorageType.any;
            default: throw new IllegalStateException("Illegal storage-type value '" + storageType.asString() + "'");
        }
    }

    private static String storageTypeToString(NodeResources.StorageType storageType) {
        switch (storageType) {
            case remote : return "remote";
            case local : return "local";
            case any : return "any";
            default: throw new IllegalStateException("Illegal storage-type value '" + storageType + "'");
        }
    }

}
