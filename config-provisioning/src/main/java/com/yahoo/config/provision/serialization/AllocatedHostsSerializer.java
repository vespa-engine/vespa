// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.config.provision.serialization;

import com.yahoo.component.Version;
import com.yahoo.config.provision.AllocatedHosts;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.HostSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.Probe;
import com.yahoo.config.provision.SidecarSpec;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.config.provision.ZoneEndpoint.AllowedUrn;
import com.yahoo.config.provision.ZoneEndpoint.AccessType;
import com.yahoo.config.provision.zone.AuthMethod;
import com.yahoo.slime.ArrayTraverser;
import com.yahoo.slime.Cursor;
import com.yahoo.slime.Inspector;
import com.yahoo.slime.ObjectTraverser;
import com.yahoo.slime.Slime;
import com.yahoo.slime.SlimeUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The hosts allocated to an application.
 * This can be serialized to/from JSON.
 * This is immutable.
 *
 * @author Ulf Lilleengen
 * @author bratseth
 */
public class AllocatedHostsSerializer {

    // WARNING: Since there are multiple servers in a ZooKeeper cluster and they upgrade one by one
    //          (and rewrite all nodes on startup), changes to the serialized format must be made
    //          such that what is serialized on version N+1 can be read by version N:
    //          - ADDING FIELDS: Always ok
    //          - REMOVING FIELDS: Stop reading the field first. Stop writing it on a later version.
    //          - CHANGING THE FORMAT OF A FIELD: Don't do it bro.

    private static final String mappingKey = "mapping";
    private static final String hostSpecKey = "hostSpec";
    private static final String hostSpecHostNameKey = "hostName";
    private static final String hostSpecMembershipKey = "membership";
    private static final String loadBalancerSettingsKey = "zoneEndpoint";
    private static final String publicField = "public";
    private static final String privateField = "private";
    private static final String authMethodsField = "authMethods";
    private static final String allowedUrnsField = "allowedUrns";
    private static final String accessTypeField = "type";
    private static final String urnField = "urn";

    private static final String realResourcesKey = "realResources";
    private static final String advertisedResourcesKey = "advertisedResources";
    private static final String requestedResourcesKey = "requestedResources";
    private static final String vcpuKey = "vcpu";
    private static final String memoryKey = "memory";
    private static final String diskKey = "disk";
    private static final String bandwidthKey = "bandwidth";
    private static final String diskSpeedKey = "diskSpeed";
    private static final String storageTypeKey = "storageType";
    private static final String architectureKey = "architecture";
    private static final String gpuKey = "gpu";
    private static final String gpuTypeKey = "gpuType";
    private static final String gpuCountKey = "gpuCount";
    private static final String gpuMemoryKey = "gpuMemory";

    /** Wanted version */
    private static final String hostSpecVespaVersionKey = "vespaVersion";

    /** Wanted docker image repo */
    private static final String hostSpecDockerImageRepoKey = "dockerImageRepo";

    /** Current version */
    private static final String hostSpecCurrentVespaVersionKey = "currentVespaVersion";
    private static final String hostSpecNetworkPortsKey = "ports";

    private static final String sidecarsKey = "sidecars";

    public static byte[] toJson(AllocatedHosts allocatedHosts) throws IOException {
        Slime slime = new Slime();
        toSlime(allocatedHosts, slime.setObject());
        return SlimeUtils.toJsonBytes(slime);
    }

    public static void toSlime(AllocatedHosts allocatedHosts, Cursor cursor) {
        Cursor array = cursor.setArray(mappingKey);
        for (HostSpec host : allocatedHosts.getHosts())
            toSlime(host, array.addObject().setObject(hostSpecKey));
    }

    private static void toSlime(HostSpec host, Cursor object) {
        object.setString(hostSpecHostNameKey, host.hostname());
        host.membership().ifPresent(membership -> {
            object.setString(hostSpecMembershipKey, membership.stringValue());
            object.setString(hostSpecVespaVersionKey, membership.cluster().vespaVersion().toFullString());
            if ( ! membership.cluster().zoneEndpoint().isDefault())
                toSlime(object.setObject(loadBalancerSettingsKey), membership.cluster().zoneEndpoint());
            membership.cluster().dockerImageRepo().ifPresent(repo -> object.setString(hostSpecDockerImageRepoKey, repo.untagged()));
            
            var sidecars = membership.cluster().sidecars();
            if (!sidecars.isEmpty()) {
                sidecarsToSlime(sidecars, object.setArray(sidecarsKey));
            }
        });
        toSlime(host.realResources(), object.setObject(realResourcesKey));
        toSlime(host.advertisedResources(), object.setObject(advertisedResourcesKey));
        host.requestedResources().ifPresent(resources -> toSlime(resources, object.setObject(requestedResourcesKey)));
        host.version().ifPresent(version -> object.setString(hostSpecCurrentVespaVersionKey, version.toFullString()));
        host.networkPorts().ifPresent(ports -> NetworkPortsSerializer.toSlime(ports, object.setArray(hostSpecNetworkPortsKey)));
    }

    private static void toSlime(NodeResources resources, Cursor resourcesObject) {
        resourcesObject.setDouble(vcpuKey, resources.vcpu());
        resourcesObject.setDouble(memoryKey, resources.memoryGiB());
        resourcesObject.setDouble(diskKey, resources.diskGb());
        resourcesObject.setDouble(bandwidthKey, resources.bandwidthGbps());
        resourcesObject.setString(diskSpeedKey, diskSpeedToString(resources.diskSpeed()));
        resourcesObject.setString(storageTypeKey, storageTypeToString(resources.storageType()));
        resourcesObject.setString(architectureKey, architectureToString(resources.architecture()));
        if (!resources.gpuResources().isDefault()) {
            Cursor gpuObject = resourcesObject.setObject(gpuKey);
            gpuObject.setString(gpuTypeKey, resources.gpuResources().type().toString());
            gpuObject.setLong(gpuCountKey, resources.gpuResources().count());
            gpuObject.setDouble(gpuMemoryKey, resources.gpuResources().memoryGiB());
        }
    }

    private static void sidecarsToSlime(List<SidecarSpec> sidecars, Cursor arrayCursor) {
        for (var sidecar : sidecars) {
            var cursor = arrayCursor.addObject();
            cursor.setLong("id", sidecar.id());
            cursor.setString("name", sidecar.name());
            cursor.setString("image", sidecar.image().asString());

            var resourcesCursor = cursor.setObject("resources");
            var resources = sidecar.resources();
            resourcesCursor.setDouble("maxCpu", resources.maxCpu());
            resourcesCursor.setDouble("minCpu", resources.minCpu());
            resourcesCursor.setDouble("memoryGiB", resources.memoryGiB());
            resourcesCursor.setBool("hasGpu", resources.hasGpu());

            var volumeMountsCursor = cursor.setArray("volumeMounts");
            sidecar.volumeMounts().forEach(volumeMountsCursor::addString);

            var envsCursor = cursor.setObject("envs");
            sidecar.envs().forEach(envsCursor::setString);

            var commandCursor = cursor.setArray("command");
            sidecar.command().forEach(commandCursor::addString);

            sidecar.livenessProbe().ifPresent(probe -> probeToSlime(probe, cursor.setObject("livenessProbe")));
        }
    }

    public static AllocatedHosts fromJson(byte[] json) {
        return fromSlime(SlimeUtils.jsonToSlime(json).get());
    }

    public static AllocatedHosts fromSlime(Inspector inspector) {
        Inspector array = inspector.field(mappingKey);
        Set<HostSpec> hosts = new LinkedHashSet<>();
        array.traverse((ArrayTraverser)(i, host) -> {
            hosts.add(hostFromSlime(host.field(hostSpecKey)));
        });
        return AllocatedHosts.withHosts(hosts);
    }

    private static HostSpec hostFromSlime(Inspector object) {
        if (object.field(hostSpecMembershipKey).valid()) { // Hosted
            return new HostSpec(object.field(hostSpecHostNameKey).asString(),
                                nodeResourcesFromSlime(object.field(realResourcesKey)),
                                nodeResourcesFromSlime(object.field(advertisedResourcesKey)),
                                optionalNodeResourcesFromSlime(object.field(requestedResourcesKey)), // TODO: Make non-optional when we serialize NodeResources.unspecified()
                                membershipFromSlime(object),
                                optionalString(object.field(hostSpecCurrentVespaVersionKey)).map(Version::new),
                                NetworkPortsSerializer.fromSlime(object.field(hostSpecNetworkPortsKey)),
                                optionalDockerImage(object.field(hostSpecDockerImageRepoKey)));
        }
        else {
            return new HostSpec(object.field(hostSpecHostNameKey).asString(),
                                NetworkPortsSerializer.fromSlime(object.field(hostSpecNetworkPortsKey)));
        }
    }

    private static NodeResources nodeResourcesFromSlime(Inspector resources) {
        return new NodeResources(resources.field(vcpuKey).asDouble(),
                                 resources.field(memoryKey).asDouble(),
                                 resources.field(diskKey).asDouble(),
                                 resources.field(bandwidthKey).asDouble(),
                                 diskSpeedFromSlime(resources.field(diskSpeedKey)),
                                 storageTypeFromSlime(resources.field(storageTypeKey)),
                                 architectureFromSlime(resources.field(architectureKey)),
                                 gpuResourcesFromSlime(resources.field(gpuKey)));
    }

    private static NodeResources.GpuResources gpuResourcesFromSlime(Inspector gpu) {
        if (!gpu.valid()) return NodeResources.GpuResources.getDefault();
        return new NodeResources.GpuResources(gpu.field(gpuTypeKey).asString(),
                                              (int) gpu.field(gpuCountKey).asLong(),
                                              gpu.field(gpuMemoryKey).asDouble());
    }

    private static NodeResources optionalNodeResourcesFromSlime(Inspector resources) {
        if ( ! resources.valid()) return NodeResources.unspecified();
        return nodeResourcesFromSlime(resources);
    }

    private static NodeResources.DiskSpeed diskSpeedFromSlime(Inspector diskSpeed) {
        return switch (diskSpeed.asString()) {
            case "fast" -> NodeResources.DiskSpeed.fast;
            case "slow" -> NodeResources.DiskSpeed.slow;
            case "any" -> NodeResources.DiskSpeed.any;
            default -> throw new IllegalStateException("Illegal disk-speed value '" + diskSpeed.asString() + "'");
        };
    }

    private static String diskSpeedToString(NodeResources.DiskSpeed diskSpeed) {
        return switch (diskSpeed) {
            case fast -> "fast";
            case slow -> "slow";
            case any -> "any";
        };
    }

    private static NodeResources.StorageType storageTypeFromSlime(Inspector storageType) {
        return switch (storageType.asString()) {
            case "remote" -> NodeResources.StorageType.remote;
            case "local" -> NodeResources.StorageType.local;
            case "any" -> NodeResources.StorageType.any;
            default -> throw new IllegalStateException("Illegal storage-type value '" + storageType.asString() + "'");
        };
    }

    private static String storageTypeToString(NodeResources.StorageType storageType) {
        return switch (storageType) {
            case remote -> "remote";
            case local -> "local";
            case any -> "any";
        };
    }

    private static NodeResources.Architecture architectureFromSlime(Inspector architecture) {
        if ( ! architecture.valid()) return NodeResources.Architecture.x86_64;
        return switch (architecture.asString()) {
            case "x86_64" -> NodeResources.Architecture.x86_64;
            case "arm64" -> NodeResources.Architecture.arm64;
            case "any" -> NodeResources.Architecture.any;
            default -> throw new IllegalStateException("Illegal architecture value '" + architecture.asString() + "'");
        };
    }

    private static String architectureToString(NodeResources.Architecture architecture) {
        return switch (architecture) {
            case x86_64 -> "x86_64";
            case arm64 -> "arm64";
            case any -> "any";
        };
    }

    private static ClusterMembership membershipFromSlime(Inspector object) {
        return ClusterMembership.from(object.field(hostSpecMembershipKey).asString(),
                                      Version.fromString(object.field(hostSpecVespaVersionKey).asString()),
                                      object.field(hostSpecDockerImageRepoKey).valid()
                                      ? Optional.of(DockerImage.fromString(object.field(hostSpecDockerImageRepoKey).asString()))
                                      : Optional.empty(),
                                      zoneEndpoint(object.field(loadBalancerSettingsKey)), 
                                      sidecarsFromSlime(object.field(sidecarsKey)));
    }

    private static void toSlime(Cursor settingsObject, ZoneEndpoint settings) {
        settingsObject.setBool(publicField, settings.isPublicEndpoint());
        settingsObject.setBool(privateField, settings.isPrivateEndpoint());

        Cursor authMethods = settingsObject.setArray(authMethodsField);
        for(AuthMethod method : settings.authMethods()) {
            authMethods.addString(method.name());
        }

        if (settings.isPrivateEndpoint()) {
            Cursor allowedUrnsArray = settingsObject.setArray(allowedUrnsField);
            for (AllowedUrn urn : settings.allowedUrns()) {
                Cursor urnObject = allowedUrnsArray.addObject();
                urnObject.setString(urnField, urn.urn());
                urnObject.setString(accessTypeField,
                                    switch (urn.type()) {
                                        case awsPrivateLink -> "awsPrivateLink";
                                        case gcpServiceConnect -> "gcpServiceConnect";
                                    });
            }
        }
    }

    private static ZoneEndpoint zoneEndpoint(Inspector settingsObject) {
        if ( ! settingsObject.valid()) return ZoneEndpoint.defaultEndpoint;
        return new ZoneEndpoint(settingsObject.field(publicField).asBool(),
                                settingsObject.field(privateField).asBool(),
                                SlimeUtils.entriesStream(settingsObject.field(authMethodsField))
                                        .map(value -> AuthMethod.valueOf(value.asString()))
                                        .toList(),
                                SlimeUtils.entriesStream(settingsObject.field(allowedUrnsField))
                                          .map(urnObject -> new AllowedUrn(switch (urnObject.field(accessTypeField).asString()) {
                                                                               case "awsPrivateLink" ->  AccessType.awsPrivateLink;
                                                                               case "gcpServiceConnect" -> AccessType.gcpServiceConnect;
                                                                               default -> throw new IllegalArgumentException("unknown service access type in '" + urnObject + "'");
                                                                           },
                                                                           urnObject.field(urnField).asString()))
                                          .toList());
    }

    private static Optional<String> optionalString(Inspector inspector) {
        if ( ! inspector.valid()) return Optional.empty();
        return Optional.of(inspector.asString());
    }

    private static Optional<DockerImage> optionalDockerImage(Inspector inspector) {
        if ( ! inspector.valid()) return Optional.empty();
        return Optional.of(DockerImage.fromString(inspector.asString()));
    }

    private static List<SidecarSpec> sidecarsFromSlime(Inspector arrayInspector) {
        var sidecars = new ArrayList<SidecarSpec>();

        arrayInspector.traverse((ArrayTraverser) (specIdx, specInspector) -> {
            var id = specInspector.field("id").asLong();
            var name = specInspector.field("name").asString();
            var image = DockerImage.fromString(specInspector.field("image").asString());

            var resourcesInspector = specInspector.field("resources");
            var maxCpu = resourcesInspector.field("maxCpu").asDouble();
            var minCpu = resourcesInspector.field("minCpu").asDouble();
            var memoryGiB = resourcesInspector.field("memoryGiB").asDouble();
            var hasGpu = resourcesInspector.field("hasGpu").asBool();

            var volumeMounts = new ArrayList<String>();
            specInspector.field("volumeMounts").traverse((ArrayTraverser) (idx, elem) -> {
                volumeMounts.add(elem.asString());
            });

            var envs = new HashMap<String, String>();
            specInspector.field("envs").traverse((ObjectTraverser) (key, value) -> envs.put(key, value.asString()));

            var command = new ArrayList<String>();
            specInspector.field("command").traverse((ArrayTraverser) (idx, elem) -> command.add(elem.asString()));

            var builder = SidecarSpec.builder()
                    .id(id)
                    .name(name)
                    .image(image)
                    .maxCpu(maxCpu)
                    .minCpu(minCpu)
                    .memoryGiB(memoryGiB)
                    .hasGpu(hasGpu)
                    .volumeMounts(volumeMounts)
                    .envs(envs)
                    .command(command);

            var livenessProbeInspector = specInspector.field("livenessProbe");
            if (livenessProbeInspector.valid()) {
                builder.livenessProbe(probeFromSlime(livenessProbeInspector));
            }

            var sidecar = builder.build();

            sidecars.add(sidecar);
        });

        return sidecars;
    }

    private static void probeToSlime(Probe probe, Cursor cursor) {
        cursor.setLong("initialDelaySeconds", probe.initialDelaySeconds());
        cursor.setLong("periodSeconds", probe.periodSeconds());
        cursor.setLong("timeoutSeconds", probe.timeoutSeconds());
        cursor.setLong("failureThreshold", probe.failureThreshold());

        var actionCursor = cursor.setObject("action");
        var action = probe.action();
        if (action instanceof Probe.HttpGetAction httpGet) {
            actionCursor.setString("type", "httpGet");
            actionCursor.setString("path", httpGet.path());
            actionCursor.setLong("port", httpGet.port());
        } else if (action instanceof Probe.ExecAction exec) {
            actionCursor.setString("type", "exec");
            var commandCursor = actionCursor.setArray("command");
            for (String cmd : exec.command()) {
                commandCursor.addString(cmd);
            }
        }
    }

    private static Probe probeFromSlime(Inspector probeInspector) {
        int initialDelaySeconds = (int) probeInspector.field("initialDelaySeconds").asLong();
        int periodSeconds = (int) probeInspector.field("periodSeconds").asLong();
        int timeoutSeconds = (int) probeInspector.field("timeoutSeconds").asLong();
        int failureThreshold = (int) probeInspector.field("failureThreshold").asLong();

        var actionInspector = probeInspector.field("action");
        String type = actionInspector.field("type").asString();

        Probe.Action action = switch (type) {
            case "httpGet" -> new Probe.HttpGetAction(
                    actionInspector.field("path").asString(),
                    (int) actionInspector.field("port").asLong());
            case "exec" -> {
                var commandList = new ArrayList<String>();
                actionInspector.field("command").traverse((ArrayTraverser) (idx, elem) ->
                        commandList.add(elem.asString()));
                yield new Probe.ExecAction(commandList);
            }
            default -> throw new IllegalArgumentException("Unknown probe action type: " + type);
        };

        return new Probe(action, initialDelaySeconds, periodSeconds, timeoutSeconds, failureThreshold);
    }
}
