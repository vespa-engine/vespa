// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.collect.ImmutableSet;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.Flavor;
import com.yahoo.config.provision.InstanceName;
import com.yahoo.config.provision.NodeFlavors;
import com.yahoo.config.provision.NodeType;
import com.yahoo.config.provision.TenantName;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.Slime;
import com.yahoo.vespa.config.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.Status;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Serializes a node to/from JSON.
 * Instances of this are multithread safe and can be reused
 *
 * @author bratseth
 */
public class NodeSerializer {

    /** The configured node flavors */
    private final NodeFlavors flavors;

    // Node fields
    private static final String hostnameKey = "hostname";
    private static final String ipAddressesKey = "ipAddresses";
    private static final String additionalIpAddressesKey = "additionalIpAddresses";
    private static final String openStackIdKey = "openStackId";
    private static final String parentHostnameKey = "parentHostname";
    private static final String historyKey = "history";
    private static final String instanceKey = "instance"; // legacy name, TODO: change to allocation with backwards compat
    private static final String rebootGenerationKey = "rebootGeneration";
    private static final String currentRebootGenerationKey = "currentRebootGeneration";
    private static final String vespaVersionKey = "vespaVersion";
    private static final String failCountKey = "failCount";
    private static final String hardwareFailureKey = "hardwareFailure";
    private static final String nodeTypeKey = "type";
    private static final String wantToRetireKey = "wantToRetire";
    private static final String wantToDeprovisionKey = "wantToDeprovision";
    private static final String hardwareDivergenceKey = "hardwareDivergence";

    // Configuration fields
    private static final String flavorKey = "flavor";

    // Allocation fields
    private static final String tenantIdKey = "tenantId";
    private static final String applicationIdKey = "applicationId";
    private static final String instanceIdKey = "instanceId";
    private static final String serviceIdKey = "serviceId"; // legacy name, TODO: change to membership with backwards compat
    private static final String restartGenerationKey = "restartGeneration";
    private static final String currentRestartGenerationKey = "currentRestartGeneration";
    private static final String removableKey = "removable";
    // Saved as part of allocation instead of serviceId, since serviceId serialized form is not easily extendable.
    private static final String wantedVespaVersionKey = "wantedVespaVersion";

    // History event fields
    private static final String historyEventTypeKey = "type";
    private static final String atKey = "at";
    private static final String agentKey = "agent"; // retired events only

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
        toSlime(node.ipAddresses(), object.setArray(ipAddressesKey));
        toSlime(node.additionalIpAddresses(), object.setArray(additionalIpAddressesKey));
        object.setString(openStackIdKey, node.openStackId());
        node.parentHostname().ifPresent(hostname -> object.setString(parentHostnameKey, hostname));
        object.setString(flavorKey, node.flavor().name());
        object.setLong(rebootGenerationKey, node.status().reboot().wanted());
        object.setLong(currentRebootGenerationKey, node.status().reboot().current());
        node.status().vespaVersion().ifPresent(version -> object.setString(vespaVersionKey, version.toString()));
        object.setLong(failCountKey, node.status().failCount());
        node.status().hardwareFailureDescription().ifPresent(failure -> object.setString(hardwareFailureKey, failure));
        object.setBool(wantToRetireKey, node.status().wantToRetire());
        object.setBool(wantToDeprovisionKey, node.status().wantToDeprovision());
        node.allocation().ifPresent(allocation -> toSlime(allocation, object.setObject(instanceKey)));
        toSlime(node.history(), object.setArray(historyKey));
        object.setString(nodeTypeKey, toString(node.type()));
        node.status().hardwareDivergence().ifPresent(hardwareDivergence -> object.setString(hardwareDivergenceKey,
                                                                                            hardwareDivergence));
    }

    private void toSlime(Allocation allocation, Cursor object) {
        object.setString(tenantIdKey, allocation.owner().tenant().value());
        object.setString(applicationIdKey, allocation.owner().application().value());
        object.setString(instanceIdKey, allocation.owner().instance().value());
        object.setString(serviceIdKey, allocation.membership().stringValue());
        object.setLong(restartGenerationKey, allocation.restartGeneration().wanted());
        object.setLong(currentRestartGenerationKey, allocation.restartGeneration().current());
        object.setBool(removableKey, allocation.isRemovable());
        object.setString(wantedVespaVersionKey, allocation.membership().cluster().vespaVersion().toString());
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

    private void toSlime(Set<String> ipAddresses, Cursor array) {
        ipAddresses.forEach(array::addString);
    }

    // ---------------- Deserialization --------------------------------------------------

    public Node fromJson(Node.State state, byte[] data) {
        return nodeFromSlime(state, SlimeUtils.jsonToSlime(data).get());
    }

    private Node nodeFromSlime(Node.State state, Inspector object) {
        return new Node(object.field(openStackIdKey).asString(),
                        ipAddressesFromSlime(object, ipAddressesKey),
                        ipAddressesFromSlime(object, additionalIpAddressesKey),
                        object.field(hostnameKey).asString(),
                        parentHostnameFromSlime(object),
                        flavorFromSlime(object),
                        statusFromSlime(object),
                        state,
                        allocationFromSlime(object.field(instanceKey)),
                        historyFromSlime(object.field(historyKey)),
                        nodeTypeFromString(object.field(nodeTypeKey).asString()));
    }

    private Status statusFromSlime(Inspector object) {
        // TODO: Simplify after June 2017
        boolean wantToDeprovision = object.field(wantToDeprovisionKey).valid() && object.field(wantToDeprovisionKey).asBool();
        return new Status(generationFromSlime(object, rebootGenerationKey, currentRebootGenerationKey),
                          versionFromSlime(object.field(vespaVersionKey)),
                          (int)object.field(failCountKey).asLong(),
                          hardwareFailureDescriptionFromSlime(object),
                          object.field(wantToRetireKey).asBool(),
                          wantToDeprovision,
                          removeQuotedNulls(hardwareDivergenceFromSlime(object)));
    }

    private Flavor flavorFromSlime(Inspector object) {
        return flavors.getFlavorOrThrow(object.field(flavorKey).asString());
    }

    private Optional<Allocation> allocationFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new Allocation(applicationIdFromSlime(object),
                                          clusterMembershipFromSlime(object),
                                          generationFromSlime(object, restartGenerationKey, currentRestartGenerationKey),
                                          object.field(removableKey).asBool()));
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
                                      versionFromSlime(object.field(wantedVespaVersionKey)).get());
    }

    private Optional<Version> versionFromSlime(Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(Version.fromString(object.asString()));
    }

    private Optional<String> parentHostnameFromSlime(Inspector object) {
        if (object.field(parentHostnameKey).valid())
            return Optional.of(object.field(parentHostnameKey).asString());
        else
            return Optional.empty();
    }

    private Optional<String> hardwareDivergenceFromSlime(Inspector object) {
        if (object.field(hardwareDivergenceKey).valid()) {
            return Optional.of(object.field(hardwareDivergenceKey).asString());
        }
        return Optional.empty();
    }

    // Remove when we no longer have "null" strings for this field in the node repo
    private Optional<String> removeQuotedNulls(Optional<String> value) {
        return value.filter(v -> !v.equals("null"));
    }


    private Set<String> ipAddressesFromSlime(Inspector object, String key) {
        ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
        object.field(key).traverse((ArrayTraverser) (i, item) -> ipAddresses.add(item.asString()));
        return ipAddresses.build();
    }

    private Optional<String> hardwareFailureDescriptionFromSlime(Inspector object) {
        if (object.field(hardwareFailureKey).valid()) {
            return Optional.of(object.field(hardwareFailureKey).asString());
        }
        return Optional.empty();
    }

    // Enum <-> string mappings
    
    /** Returns the event type, or null if this event type should be ignored */
    private History.Event.Type eventTypeFromString(String eventTypeString) {
        switch (eventTypeString) {
            case "provisioned" : return History.Event.Type.provisioned;
            case "readied" : return History.Event.Type.readied;
            case "reserved" : return History.Event.Type.reserved;
            case "activated" : return History.Event.Type.activated;
            case "retired" : return History.Event.Type.retired;
            case "deactivated" : return History.Event.Type.deactivated;
            case "parked" : return History.Event.Type.parked;
            case "failed" : return History.Event.Type.failed;
            case "deallocated" : return History.Event.Type.deallocated;
            case "down" : return History.Event.Type.down;
            case "requested" : return History.Event.Type.requested;
            case "rebooted" : return History.Event.Type.rebooted;
        }
        throw new IllegalArgumentException("Unknown node event type '" + eventTypeString + "'");
    }
    private String toString(History.Event.Type nodeEventType) {
        switch (nodeEventType) {
            case provisioned : return "provisioned";
            case readied : return "readied";
            case reserved : return "reserved";
            case activated : return "activated";
            case retired : return "retired";
            case deactivated : return "deactivated";
            case parked : return "parked";
            case failed : return "failed";
            case deallocated : return "deallocated";
            case down : return "down";
            case requested: return "requested";
            case rebooted: return "rebooted";
        }
        throw new IllegalArgumentException("Serialized form of '" + nodeEventType + "' not defined");
    }

    private Agent eventAgentFromSlime(Inspector eventAgentField) {
        if ( ! eventAgentField.valid()) return Agent.system; // TODO: Remove after April 2017

        switch (eventAgentField.asString()) {
            case "application" : return Agent.application;
            case "system" : return Agent.system;
            case "operator" : return Agent.operator;
            case "NodeRetirer" : return Agent.NodeRetirer;
        }
        throw new IllegalArgumentException("Unknown node event agent '" + eventAgentField.asString() + "'");
    }
    private String toString(Agent agent) {
        switch (agent) {
            case application : return "application";
            case system : return "system";
            case operator : return "operator";
            case NodeRetirer : return "NodeRetirer";
        }
        throw new IllegalArgumentException("Serialized form of '" + agent + "' not defined");
    }

    private NodeType nodeTypeFromString(String typeString) {
        switch (typeString) {
            case "tenant" : return NodeType.tenant;
            case "host" : return NodeType.host;
            case "proxy" : return NodeType.proxy;
            case "proxyhost" : return NodeType.proxyhost;
            case "config" : return NodeType.config;
            case "confighost" : return NodeType.confighost;
            default : throw new IllegalArgumentException("Unknown node type '" + typeString + "'");
        }
    }
    private String toString(NodeType type) {
        switch (type) {
            case tenant: return "tenant";
            case host: return "host";
            case proxy: return "proxy";
            case proxyhost: return "proxyhost";
            case config: return "config";
            case confighost: return "confighost";
        }
        throw new IllegalArgumentException("Serialized form of '" + type + "' not defined");
    }

}
