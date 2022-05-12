// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.hosted.provision.persistence;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableSet;
import com.google.common.hash.Hashing;
import com.google.common.util.concurrent.UncheckedExecutionException;
import com.yahoo.component.Version;
import com.yahoo.config.provision.ApplicationId;
import com.yahoo.config.provision.ApplicationName;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
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
import com.yahoo.slime.SlimeUtils;
import com.yahoo.vespa.hosted.provision.Node;
import com.yahoo.vespa.hosted.provision.node.Address;
import com.yahoo.vespa.hosted.provision.node.Agent;
import com.yahoo.vespa.hosted.provision.node.Allocation;
import com.yahoo.vespa.hosted.provision.node.Generation;
import com.yahoo.vespa.hosted.provision.node.History;
import com.yahoo.vespa.hosted.provision.node.IP;
import com.yahoo.vespa.hosted.provision.node.OsVersion;
import com.yahoo.vespa.hosted.provision.node.Reports;
import com.yahoo.vespa.hosted.provision.node.Status;
import com.yahoo.vespa.hosted.provision.node.TrustStoreItem;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

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
    private static final String containersKey = "containers";
    private static final String containerHostnameKey = "hostname";
    private static final String idKey = "openStackId";
    private static final String parentHostnameKey = "parentHostname";
    private static final String historyKey = "history";
    private static final String logKey = "log";
    private static final String instanceKey = "instance"; // legacy name, TODO: change to allocation with backwards compat
    private static final String rebootGenerationKey = "rebootGeneration";
    private static final String currentRebootGenerationKey = "currentRebootGeneration";
    private static final String vespaVersionKey = "vespaVersion";
    private static final String currentContainerImageKey = "currentDockerImage";
    private static final String failCountKey = "failCount";
    private static final String nodeTypeKey = "type";
    private static final String wantToRetireKey = "wantToRetire";
    private static final String wantToDeprovisionKey = "wantToDeprovision";
    private static final String wantToRebuildKey = "wantToRebuild";
    private static final String preferToRetireKey = "preferToRetire";
    private static final String wantToFailKey = "wantToFailKey";
    private static final String osVersionKey = "osVersion";
    private static final String wantedOsVersionKey = "wantedOsVersion";
    private static final String firmwareCheckKey = "firmwareCheck";
    private static final String reportsKey = "reports";
    private static final String modelNameKey = "modelName";
    private static final String reservedToKey = "reservedTo";
    private static final String exclusiveToApplicationIdKey = "exclusiveTo";
    private static final String exclusiveToClusterTypeKey = "exclusiveToClusterType";
    private static final String switchHostnameKey = "switchHostname";
    private static final String trustedCertificatesKey = "trustedCertificates";

    // Node resource fields
    private static final String flavorKey = "flavor";
    private static final String resourcesKey = "resources";
    private static final String diskKey = "disk";

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
    private static final String wantedContainerImageRepoKey = "wantedDockerImageRepo";

    // History event fields
    private static final String historyEventTypeKey = "type";
    private static final String atKey = "at";
    private static final String agentKey = "agent"; // retired events only

    // Network port fields
    private static final String networkPortsKey = "networkPorts";

    // Trusted certificates fields
    private static final String fingerprintKey = "fingerprint";
    private static final String expiresKey = "expires";

    // A cache of deserialized Node objects. The cache is keyed on the hash of serialized node data.
    //
    // Deserializing a Node from slime is expensive, and happens frequently. Node instances that have already been
    // deserialized are returned from this cache instead of being deserialized again.
    private final Cache<Long, Node> cache;

    // ---------------- Serialization ----------------------------------------------------

    public NodeSerializer(NodeFlavors flavors, long cacheSize) {
        this.flavors = flavors;
        this.cache = CacheBuilder.newBuilder().maximumSize(cacheSize).recordStats().build();
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

    /** Returns cache statistics for this serializer */
    public CacheStats cacheStats() {
        var stats = cache.stats();
        return new CacheStats(stats.hitRate(), stats.evictionCount(), cache.size());
    }

    private void toSlime(Node node, Cursor object) {
        object.setString(hostnameKey, node.hostname());
        toSlime(node.ipConfig().primary(), object.setArray(ipAddressesKey));
        toSlime(node.ipConfig().pool().ipSet(), object.setArray(ipAddressPoolKey));
        toSlime(node.ipConfig().pool().getAddressList(), object);
        object.setString(idKey, node.id());
        node.parentHostname().ifPresent(hostname -> object.setString(parentHostnameKey, hostname));
        toSlime(node.flavor(), object);
        object.setLong(rebootGenerationKey, node.status().reboot().wanted());
        object.setLong(currentRebootGenerationKey, node.status().reboot().current());
        node.status().vespaVersion().ifPresent(version -> object.setString(vespaVersionKey, version.toString()));
        node.status().containerImage().ifPresent(image -> object.setString(currentContainerImageKey, image.asString()));
        object.setLong(failCountKey, node.status().failCount());
        object.setBool(wantToRetireKey, node.status().wantToRetire());
        object.setBool(preferToRetireKey, node.status().preferToRetire());
        object.setBool(wantToDeprovisionKey, node.status().wantToDeprovision());
        object.setBool(wantToFailKey, node.status().wantToFail());
        object.setBool(wantToRebuildKey, node.status().wantToRebuild());
        node.allocation().ifPresent(allocation -> toSlime(allocation, object.setObject(instanceKey)));
        toSlime(node.history().events(), object.setArray(historyKey));
        toSlime(node.history().log(), object.setArray(logKey));
        object.setString(nodeTypeKey, toString(node.type()));
        node.status().osVersion().current().ifPresent(version -> object.setString(osVersionKey, version.toString()));
        node.status().osVersion().wanted().ifPresent(version -> object.setString(wantedOsVersionKey, version.toFullString()));
        node.status().firmwareVerifiedAt().ifPresent(instant -> object.setLong(firmwareCheckKey, instant.toEpochMilli()));
        node.switchHostname().ifPresent(switchHostname -> object.setString(switchHostnameKey, switchHostname));
        node.reports().toSlime(object, reportsKey);
        node.modelName().ifPresent(modelName -> object.setString(modelNameKey, modelName));
        node.reservedTo().ifPresent(tenant -> object.setString(reservedToKey, tenant.value()));
        node.exclusiveToApplicationId().ifPresent(applicationId -> object.setString(exclusiveToApplicationIdKey, applicationId.serializedForm()));
        node.exclusiveToClusterType().ifPresent(clusterType -> object.setString(exclusiveToClusterTypeKey, clusterType.name()));
        trustedCertificatesToSlime(node.trustedCertificates(), object.setArray(trustedCertificatesKey));
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
            NodeResourcesSerializer.toSlime(flavor.resources(), object.setObject(resourcesKey));
        }
    }

    private void toSlime(Allocation allocation, Cursor object) {
        NodeResourcesSerializer.toSlime(allocation.requestedResources(), object.setObject(requestedResourcesKey));
        object.setString(tenantIdKey, allocation.owner().tenant().value());
        object.setString(applicationIdKey, allocation.owner().application().value());
        object.setString(instanceIdKey, allocation.owner().instance().value());
        object.setString(serviceIdKey, allocation.membership().stringValue());
        object.setLong(restartGenerationKey, allocation.restartGeneration().wanted());
        object.setLong(currentRestartGenerationKey, allocation.restartGeneration().current());
        object.setBool(removableKey, allocation.isRemovable());
        object.setString(wantedVespaVersionKey, allocation.membership().cluster().vespaVersion().toString());
        allocation.membership().cluster().dockerImageRepo().ifPresent(repo -> object.setString(wantedContainerImageRepoKey, repo.untagged()));
        allocation.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray(networkPortsKey)));
    }

    private void toSlime(Collection<History.Event> events, Cursor array) {
        for (History.Event event : events)
            toSlime(event, array.addObject());
    }

    private void toSlime(History.Event event, Cursor object) {
        object.setString(historyEventTypeKey, toString(event.type()));
        object.setLong(atKey, event.at().toEpochMilli());
        object.setString(agentKey, toString(event.agent()));
    }

    private void toSlime(Set<String> ipAddresses, Cursor array) {
        // Validating IP address string literals is expensive, so we do it at serialization time instead of Node
        // construction time
        ipAddresses.stream().map(IP::parse).sorted(IP.NATURAL_ORDER).map(IP::asString).forEach(array::addString);
    }

    private void toSlime(List<Address> addresses, Cursor object) {
        if (addresses.isEmpty()) return;
        Cursor addressCursor = object.setArray(containersKey);
        addresses.forEach(address -> {
            addressCursor.addObject().setString(containerHostnameKey, address.hostname());
        });
    }

    private void trustedCertificatesToSlime(List<TrustStoreItem> trustStoreItems, Cursor array) {
        trustStoreItems.forEach(cert -> {
            Cursor object = array.addObject();
            object.setString(fingerprintKey, cert.fingerprint());
            object.setLong(expiresKey, cert.expiry().toEpochMilli());
        });
    }

    // ---------------- Deserialization --------------------------------------------------

    public Node fromJson(Node.State state, byte[] data) {
        long key = Hashing.sipHash24().newHasher()
                          .putString(state.name(), StandardCharsets.UTF_8)
                          .putBytes(data).hash()
                          .asLong();
        try {
            return cache.get(key, () -> nodeFromSlime(state, SlimeUtils.jsonToSlime(data).get()));
        } catch (ExecutionException e) {
            throw new UncheckedExecutionException(e);
        }
    }

    private Node nodeFromSlime(Node.State state, Inspector object) {
        Flavor flavor = flavorFromSlime(object);
        return new Node(object.field(idKey).asString(),
                        new IP.Config(ipAddressesFromSlime(object, ipAddressesKey),
                                      ipAddressesFromSlime(object, ipAddressPoolKey),
                                      addressesFromSlime(object)),
                        object.field(hostnameKey).asString(),
                        SlimeUtils.optionalString(object.field(parentHostnameKey)),
                        flavor,
                        statusFromSlime(object),
                        state,
                        allocationFromSlime(flavor.resources(), object.field(instanceKey)),
                        historyFromSlime(object),
                        nodeTypeFromString(object.field(nodeTypeKey).asString()),
                        Reports.fromSlime(object.field(reportsKey)),
                        SlimeUtils.optionalString(object.field(modelNameKey)),
                        SlimeUtils.optionalString(object.field(reservedToKey)).map(TenantName::from),
                        SlimeUtils.optionalString(object.field(exclusiveToApplicationIdKey)).map(ApplicationId::fromSerializedForm),
                        SlimeUtils.optionalString(object.field(exclusiveToClusterTypeKey)).map(ClusterSpec.Type::from),
                        SlimeUtils.optionalString(object.field(switchHostnameKey)),
                        trustedCertificatesFromSlime(object));
    }

    private Status statusFromSlime(Inspector object) {
        return new Status(generationFromSlime(object, rebootGenerationKey, currentRebootGenerationKey),
                          versionFromSlime(object.field(vespaVersionKey)),
                          containerImageFromSlime(object.field(currentContainerImageKey)),
                          (int) object.field(failCountKey).asLong(),
                          object.field(wantToRetireKey).asBool(),
                          object.field(wantToDeprovisionKey).asBool(),
                          object.field(wantToRebuildKey).asBool(),
                          object.field(preferToRetireKey).asBool(),
                          object.field(wantToFailKey).asBool(),
                          new OsVersion(versionFromSlime(object.field(osVersionKey)),
                                        versionFromSlime(object.field(wantedOsVersionKey))),
                          SlimeUtils.optionalInstant(object.field(firmwareCheckKey)));
    }

    private Flavor flavorFromSlime(Inspector object) {
        Inspector resources = object.field(resourcesKey);

        if (object.field(flavorKey).valid()) {
            Flavor flavor = flavors.getFlavorOrThrow(object.field(flavorKey).asString());
            if (!resources.valid()) return flavor;
            return flavor.with(FlavorOverrides.ofDisk(resources.field(diskKey).asDouble()));
        }
        else {
            return new Flavor(NodeResourcesSerializer.resourcesFromSlime(resources));
        }
    }

    private Optional<Allocation> allocationFromSlime(NodeResources assignedResources, Inspector object) {
        if ( ! object.valid()) return Optional.empty();
        return Optional.of(new Allocation(applicationIdFromSlime(object),
                                          clusterMembershipFromSlime(object),
                                          NodeResourcesSerializer.optionalResourcesFromSlime(object.field(requestedResourcesKey))
                                                                 .orElse(assignedResources),
                                          generationFromSlime(object, restartGenerationKey, currentRestartGenerationKey),
                                          object.field(removableKey).asBool(),
                                          NetworkPortsSerializer.fromSlime(object.field(networkPortsKey))));
    }

    private ApplicationId applicationIdFromSlime(Inspector object) {
        return ApplicationId.from(TenantName.from(object.field(tenantIdKey).asString()),
                                  ApplicationName.from(object.field(applicationIdKey).asString()),
                                  InstanceName.from(object.field(instanceIdKey).asString()));
    }

    private History historyFromSlime(Inspector object) {
        return new History(eventsFromSlime(object.field(historyKey)),
                           eventsFromSlime(object.field(logKey)));
    }

    private List<History.Event> eventsFromSlime(Inspector array) {
        if (!array.valid()) return List.of();
        List<History.Event> events = new ArrayList<>();
        array.traverse((ArrayTraverser) (int i, Inspector item) -> {
            History.Event event = eventFromSlime(item);
            if (event != null)
                events.add(event);
        });
        return events;
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
                                      containerImageFromSlime(object.field(wantedContainerImageRepoKey)));
    }

    private Optional<Version> versionFromSlime(Inspector object) {
        return object.valid() ? Optional.of(Version.fromString(object.asString())) : Optional.empty();
    }

    private Optional<DockerImage> containerImageFromSlime(Inspector object) {
        return SlimeUtils.optionalString(object).map(DockerImage::fromString);
    }

    private Set<String> ipAddressesFromSlime(Inspector object, String key) {
        ImmutableSet.Builder<String> ipAddresses = ImmutableSet.builder();
        object.field(key).traverse((ArrayTraverser) (i, item) -> ipAddresses.add(item.asString()));
        return ipAddresses.build();
    }

    private List<Address> addressesFromSlime(Inspector object) {
        return SlimeUtils.entriesStream(object.field(containersKey))
                         .map(elem -> new Address(elem.field(containerHostnameKey).asString()))
                         .collect(Collectors.toList());
    }

    private List<TrustStoreItem> trustedCertificatesFromSlime(Inspector object) {
        return SlimeUtils.entriesStream(object.field(trustedCertificatesKey))
                         .map(elem -> new TrustStoreItem(elem.field(fingerprintKey).asString(),
                                                         Instant.ofEpochMilli(elem.field(expiresKey).asLong())))
                         .collect(Collectors.toList());
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
            case "wantToFail": return History.Event.Type.wantToFail;
            case "retired" : return History.Event.Type.retired;
            case "deactivated" : return History.Event.Type.deactivated;
            case "parked" : return History.Event.Type.parked;
            case "failed" : return History.Event.Type.failed;
            case "deallocated" : return History.Event.Type.deallocated;
            case "down" : return History.Event.Type.down;
            case "up" : return History.Event.Type.up;
            case "requested" : return History.Event.Type.requested;
            case "resized" : return History.Event.Type.resized;
            case "rebooted" : return History.Event.Type.rebooted;
            case "osUpgraded" : return History.Event.Type.osUpgraded;
            case "firmwareVerified" : return History.Event.Type.firmwareVerified;
            case "breakfixed" : return History.Event.Type.breakfixed;
            case "preferToRetire" : return History.Event.Type.preferToRetire;
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
            case wantToFail: return "wantToFail";
            case retired : return "retired";
            case deactivated : return "deactivated";
            case parked : return "parked";
            case failed : return "failed";
            case deallocated : return "deallocated";
            case down : return "down";
            case up : return "up";
            case requested: return "requested";
            case resized: return "resized";
            case rebooted: return "rebooted";
            case osUpgraded: return "osUpgraded";
            case firmwareVerified: return "firmwareVerified";
            case breakfixed: return "breakfixed";
            case preferToRetire: return "preferToRetire";
        }
        throw new IllegalArgumentException("Serialized form of '" + nodeEventType + "' not defined");
    }

    private Agent eventAgentFromSlime(Inspector eventAgentField) {
        switch (eventAgentField.asString()) {
            case "operator" : return Agent.operator;
            case "application" : return Agent.application;
            case "system" : return Agent.system;
            case "DirtyExpirer" : return Agent.DirtyExpirer;
            case "DynamicProvisioningMaintainer" : return Agent.DynamicProvisioningMaintainer;
            case "FailedExpirer" : return Agent.FailedExpirer;
            case "InactiveExpirer" : return Agent.InactiveExpirer;
            case "NodeFailer" : return Agent.NodeFailer;
            case "NodeHealthTracker" : return Agent.NodeHealthTracker;
            case "ProvisionedExpirer" : return Agent.ProvisionedExpirer;
            case "Rebalancer" : return Agent.Rebalancer;
            case "ReservationExpirer" : return Agent.ReservationExpirer;
            case "RetiringUpgrader" : return Agent.RetiringOsUpgrader;
            case "RebuildingOsUpgrader" : return Agent.RebuildingOsUpgrader;
            case "SpareCapacityMaintainer": return Agent.SpareCapacityMaintainer;
            case "SwitchRebalancer": return Agent.SwitchRebalancer;
            case "HostEncrypter": return Agent.HostEncrypter;
            case "ParkedExpirer": return Agent.ParkedExpirer;
        }
        throw new IllegalArgumentException("Unknown node event agent '" + eventAgentField.asString() + "'");
    }
    private String toString(Agent agent) {
        switch (agent) {
            case operator : return "operator";
            case application : return "application";
            case system : return "system";
            case DirtyExpirer : return "DirtyExpirer";
            case DynamicProvisioningMaintainer : return "DynamicProvisioningMaintainer";
            case FailedExpirer : return "FailedExpirer";
            case InactiveExpirer : return "InactiveExpirer";
            case NodeFailer : return "NodeFailer";
            case NodeHealthTracker: return "NodeHealthTracker";
            case ProvisionedExpirer : return "ProvisionedExpirer";
            case Rebalancer : return "Rebalancer";
            case ReservationExpirer : return "ReservationExpirer";
            case RetiringOsUpgrader: return "RetiringUpgrader";
            case RebuildingOsUpgrader: return "RebuildingOsUpgrader";
            case SpareCapacityMaintainer: return "SpareCapacityMaintainer";
            case SwitchRebalancer: return "SwitchRebalancer";
            case HostEncrypter: return "HostEncrypter";
            case ParkedExpirer: return "ParkedExpirer";
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
        }
        throw new IllegalArgumentException("Serialized form of '" + type + "' not defined");
    }

}
