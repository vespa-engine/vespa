// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.CloudAccount;
import com.yahoo.config.provision.ClusterInfo;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.DockerImage;
import com.yahoo.config.provision.IntRange;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.ZoneEndpoint;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToDoubleFunction;

/**
 * A common utility class to represent a requirement for nodes during model building.
 * Such a requirement is commonly specified in services.xml as a <code>nodes</code> element.
 *
 * @author bratseth
 */
public class NodesSpecification {

    private final ClusterResources min, max;

    private final IntRange groupSize;

    private final boolean dedicated;

    /** The Vespa version we want the nodes to run */
    private final Version version;

    /** 
     * Whether the capacity amount specified is required or can be relaxed
     * at the discretion of the component fulfilling it
     */
    private final boolean required;

    private final boolean canFail;

    private final boolean exclusive;

    /** The repo part of a docker image (without tag), optional */
    private final Optional<DockerImage> dockerImageRepo;

    /** The ID of the cluster referencing this node specification, if any */
    private final Optional<String> combinedId;

    /** The cloud account to use for nodes in this spec, if any */
    private final Optional<CloudAccount> cloudAccount;

    /* Whether the count attribute was present on the nodes element. */
    private final boolean hasCountAttribute;

    private NodesSpecification(ClusterResources min,
                               ClusterResources max,
                               IntRange groupSize,
                               boolean dedicated, Version version,
                               boolean required, boolean canFail, boolean exclusive,
                               Optional<DockerImage> dockerImageRepo,
                               Optional<String> combinedId,
                               Optional<CloudAccount> cloudAccount,
                               boolean hasCountAttribute) {
        if (max.smallerThan(min))
            throw new IllegalArgumentException("Max resources must be larger or equal to min resources, but " +
                                               max + " is smaller than " + min);
        if (min.nodes() < 1)
            throw new IllegalArgumentException("Min node count cannot be less than 1, but is " + min.nodes());

        // Non-scaled resources must be equal
        if ( ! min.nodeResources().justNonNumbers().equals(max.nodeResources().justNonNumbers()))
            throw new IllegalArgumentException("Min and max resources must have the same non-numeric settings, but " +
                                               "min is " + min + " and max " + max);
        if (min.nodeResources().bandwidthGbps() != max.nodeResources().bandwidthGbps())
            throw new IllegalArgumentException("Min and max resources must have the same bandwidth, but " +
                                               "min is " + min + " and max " + max);

        this.min = min;
        this.max = max;
        this.groupSize = groupSize;
        this.dedicated = dedicated;
        this.version = version;
        this.required = required;
        this.canFail = canFail;
        this.exclusive = exclusive;
        this.dockerImageRepo = dockerImageRepo;
        this.combinedId = combinedId;
        this.cloudAccount = cloudAccount;
        this.hasCountAttribute = hasCountAttribute;
    }

    static NodesSpecification create(boolean dedicated, boolean canFail, Version version,
                                     ModelElement nodesElement, Optional<DockerImage> dockerImageRepo,
                                     Optional<CloudAccount> cloudAccount) {
        var resolvedElement = resolveElement(nodesElement);
        var combinedId = findCombinedId(nodesElement, resolvedElement);
        var resourceConstraints = toResourceConstraints(resolvedElement);
        boolean hasCountAttribute = resolvedElement.stringAttribute("count") != null;
        return new NodesSpecification(resourceConstraints.min,
                                      resourceConstraints.max,
                                      resourceConstraints.groupSize,
                                      dedicated,
                                      version,
                                      resolvedElement.booleanAttribute("required", false),
                                      canFail,
                                      resolvedElement.booleanAttribute("exclusive", false),
                                      dockerImageToUse(resolvedElement, dockerImageRepo),
                                      combinedId,
                                      cloudAccount,
                                      hasCountAttribute);
    }

    private static ResourceConstraints toResourceConstraints(ModelElement nodesElement) {
        var nodes =  rangeFrom(nodesElement, "count");
        var groups =  rangeFrom(nodesElement, "groups");
        var groupSize =  rangeFrom(nodesElement, "group-size");

        if (nodes.from().orElse(1) < 1)
            throw new IllegalArgumentException("Min node resources cannot be less than 1, but is " + nodes.from().getAsInt());

        // Find the tightest possible limits for groups to avoid falsely concluding we are autoscaling
        // when only specifying group size
        int defaultMinGroups =                           nodes.from().orElse(1) / groupSize.to().orElse(nodes.from().orElse(1));
        int defaultMaxGroups = groupSize.isEmpty() ? 1 : nodes.to().orElse(1) / groupSize.from().orElse(1);

        var min = new ClusterResources(nodes.from().orElse(1), groups.from().orElse(defaultMinGroups), nodeResources(nodesElement).getFirst());
        var max = new ClusterResources(nodes.to().orElse(1), groups.to().orElse(defaultMaxGroups), nodeResources(nodesElement).getSecond());
        return new ResourceConstraints(min, max, groupSize);
    }

    private static IntRange rangeFrom(ModelElement element, String name) {
        try {
            return IntRange.from(element.stringAttribute(name, ""));
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Illegal " + name + " value", e);
        }
    }

    private record ResourceConstraints(ClusterResources min, ClusterResources max, IntRange groupSize) {}

    /** Returns the ID of the cluster referencing this node specification, if any */
    private static Optional<String> findCombinedId(ModelElement nodesElement, ModelElement resolvedElement) {
        if (resolvedElement != nodesElement) {
            // Specification for a container cluster referencing nodes in a content cluster
            return containerIdOf(nodesElement);
        }
        // Specification for a content cluster that is referenced by a container cluster
        return containerIdReferencing(nodesElement);
    }

    /** Returns a requirement for dedicated nodes taken from the given <code>nodes</code> element */
    public static NodesSpecification from(ModelElement nodesElement, ConfigModelContext context) {
        return create(true,
                      ! context.getDeployState().getProperties().isBootstrap(),
                      context.getDeployState().getWantedNodeVespaVersion(),
                      nodesElement,
                      context.getDeployState().getWantedDockerImageRepo(),
                      context.getDeployState().getProperties().cloudAccount());
    }

    /**
     * Returns a requirement for non-dedicated or dedicated nodes taken from the <code>nodes</code> element
     * contained in the given parent element, or empty if the parent element is null, or the nodes elements
     * is not present.
     */
    public static Optional<NodesSpecification> optionalDedicatedFromParent(ModelElement parentElement,
                                                                           ConfigModelContext context) {
        if (parentElement == null) return Optional.empty();
        ModelElement nodesElement = parentElement.child("nodes");
        if (nodesElement == null) return Optional.empty();
        return Optional.of(create(nodesElement.booleanAttribute("dedicated", false),
                                  ! context.getDeployState().getProperties().isBootstrap(),
                                  context.getDeployState().getWantedNodeVespaVersion(),
                                  nodesElement,
                                  context.getDeployState().getWantedDockerImageRepo(),
                                  context.getDeployState().getProperties().cloudAccount()));
    }

    /**
     * Returns a requirement from <code>count</code> non-dedicated nodes in one group
     */
    public static NodesSpecification nonDedicated(int count, ConfigModelContext context) {
        return new NodesSpecification(new ClusterResources(count, 1, NodeResources.unspecified()),
                                      new ClusterResources(count, 1, NodeResources.unspecified()),
                                      IntRange.empty(),
                                      false,
                                      context.getDeployState().getWantedNodeVespaVersion(),
                                      false,
                                      ! context.getDeployState().getProperties().isBootstrap(),
                                      false,
                                      context.getDeployState().getWantedDockerImageRepo(),
                                      Optional.empty(),
                                      context.getDeployState().getProperties().cloudAccount(),
                                      false);
    }

    /** Returns a requirement from <code>count</code> dedicated nodes in one group */
    public static NodesSpecification dedicated(int count, ConfigModelContext context) {
        return new NodesSpecification(new ClusterResources(count, 1, NodeResources.unspecified()),
                                      new ClusterResources(count, 1, NodeResources.unspecified()),
                                      IntRange.empty(),
                                      true,
                                      context.getDeployState().getWantedNodeVespaVersion(),
                                      false,
                                      ! context.getDeployState().getProperties().isBootstrap(),
                                      false,
                                      context.getDeployState().getWantedDockerImageRepo(),
                                      Optional.empty(),
                                      context.getDeployState().getProperties().cloudAccount(),
                                      false);
    }

    /**
     * Returns a requirement for {@code count} shared nodes with {@code required} taken as
     * the OR over all content clusters, and with the given resources.
     */
    public static NodesSpecification requiredFromSharedParents(int count, NodeResources resources,
                                                               ModelElement element, ConfigModelContext context) {
        List<NodesSpecification> allContent = findParentByTag("services", element.getXml()).map(services -> XML.getChildren(services, "content"))
                                                                                           .orElse(List.of())
                                                                                           .stream()
                                                                                           .map(content -> new ModelElement(content).child("nodes"))
                                                                                           .filter(nodes -> nodes != null && nodes.stringAttribute("count") != null)
                                                                                           .map(nodes -> from(nodes, context))
                                                                                           .toList();
        return new NodesSpecification(new ClusterResources(count, 1, resources),
                                      new ClusterResources(count, 1, resources),
                                      IntRange.empty(),
                                      true,
                                      context.getDeployState().getWantedNodeVespaVersion(),
                                      allContent.stream().anyMatch(content -> content.required),
                                      ! context.getDeployState().getProperties().isBootstrap(),
                                      false,
                                      context.getDeployState().getWantedDockerImageRepo(),
                                      Optional.empty(),
                                      context.getDeployState().getProperties().cloudAccount(),
                                      false);
    }

    public ClusterResources minResources() { return min; }
    public ClusterResources maxResources() { return max; }
    public IntRange groupSize() { return groupSize; }

    /**
     * Returns whether this requires dedicated nodes.
     * Otherwise the model encountering this request should reuse nodes requested for other purposes whenever possible.
     */
    public boolean isDedicated() { return dedicated; }

    /**
     * Returns whether the physical hosts running the nodes of this application can
     * also run nodes of other applications. Using exclusive nodes for containers increases security
     * and increases cost.
     */
    public boolean isExclusive() { return exclusive; }

    /** Returns whether the count attribute was present on the {@code <nodes>} element. */
    public boolean hasCountAttribute() {
        return hasCountAttribute;
    }

    public Map<HostResource, ClusterMembership> provision(HostSystem hostSystem,
                                                          ClusterSpec.Type clusterType,
                                                          ClusterSpec.Id clusterId,
                                                          DeployLogger logger,
                                                          boolean stateful,
                                                          ClusterInfo clusterInfo) {
        return provision(hostSystem, clusterType, clusterId, ZoneEndpoint.defaultEndpoint, logger, stateful, clusterInfo);
    }

    public Map<HostResource, ClusterMembership> provision(HostSystem hostSystem,
                                                          ClusterSpec.Type clusterType,
                                                          ClusterSpec.Id clusterId,
                                                          ZoneEndpoint zoneEndpoint,
                                                          DeployLogger logger,
                                                          boolean stateful,
                                                          ClusterInfo info) {
        if (combinedId.isPresent())
            clusterType = ClusterSpec.Type.combined;
        ClusterSpec cluster = ClusterSpec.request(clusterType, clusterId)
                                         .vespaVersion(version)
                                         .exclusive(exclusive)
                                         .combinedId(combinedId.map(ClusterSpec.Id::from))
                                         .dockerImageRepository(dockerImageRepo)
                                         .loadBalancerSettings(zoneEndpoint)
                                         .stateful(stateful)
                                         .build();
        return hostSystem.allocateHosts(cluster, Capacity.from(min, max, groupSize, required, canFail, cloudAccount, info), logger);
    }

    private static Pair<NodeResources, NodeResources> nodeResources(ModelElement nodesElement) {
        ModelElement resources = nodesElement.child("resources");
        if (resources != null) {
            return nodeResourcesFromResourcesElement(resources);
        }
        else if (nodesElement.stringAttribute("flavor") != null) { // legacy fallback
            var flavorResources = NodeResources.fromLegacyName(nodesElement.stringAttribute("flavor"));
            return new Pair<>(flavorResources, flavorResources);
        }
        else {
            return new Pair<>(NodeResources.unspecified(), NodeResources.unspecified());
        }
    }

    private static Pair<NodeResources, NodeResources> nodeResourcesFromResourcesElement(ModelElement element) {
        Pair<Double, Double> vcpu       = toRange("vcpu", element,      .0, Double::parseDouble);
        Pair<Double, Double> memory     = toRange("memory", element,    .0, s -> parseGbAmount(s, "B"));
        Pair<Double, Double> disk       = toRange("disk", element,      .0, s -> parseGbAmount(s, "B"));
        Pair<Double, Double> bandwith   = toRange("bandwidth", element, .3, s -> parseGbAmount(s, "BPS"));
        NodeResources.DiskSpeed   diskSpeed     = parseOptionalDiskSpeed(element.stringAttribute("disk-speed"));
        NodeResources.StorageType storageType   = parseOptionalStorageType(element.stringAttribute("storage-type"));
        NodeResources.Architecture architecture = parseOptionalArchitecture(element.stringAttribute("architecture"));
        NodeResources.GpuResources gpuResources = parseOptionalGpuResources(element.child("gpu"));

        var min = new NodeResources(vcpu.getFirst(),  memory.getFirst(),  disk.getFirst(),  bandwith.getFirst(),
                                    diskSpeed, storageType, architecture, gpuResources);
        var max = new NodeResources(vcpu.getSecond(), memory.getSecond(), disk.getSecond(), bandwith.getSecond(),
                                    diskSpeed, storageType, architecture, gpuResources);
        return new Pair<>(min, max);
    }

    private static NodeResources.GpuResources parseOptionalGpuResources(ModelElement element) {
        if (element == null) return NodeResources.GpuResources.getDefault();
        int count = element.requiredIntegerAttribute("count");
        double memory = parseGbAmount(element.requiredStringAttribute("memory"), "B");
        return new NodeResources.GpuResources(count, memory);
    }

    private static double parseGbAmount(String byteAmount, String unit) {
        byteAmount = byteAmount.strip();
        byteAmount = byteAmount.toUpperCase();
        if (byteAmount.endsWith(unit))
            byteAmount = byteAmount.substring(0, byteAmount.length() - unit.length());

        double multiplier = -1;
        if (byteAmount.endsWith("K"))
            multiplier = Math.pow(1000, -2);
        else if (byteAmount.endsWith("M"))
            multiplier = Math.pow(1000, -1);
        else if (byteAmount.endsWith("G"))
            multiplier = 1;
        else if (byteAmount.endsWith("T"))
            multiplier = 1000;
        else if (byteAmount.endsWith("P"))
            multiplier = Math.pow(1000, 2);
        else if (byteAmount.endsWith("E"))
            multiplier = Math.pow(1000, 3);
        else if (byteAmount.endsWith("Z"))
            multiplier = Math.pow(1000, 4);
        else if (byteAmount.endsWith("Y"))
            multiplier = Math.pow(1000, 5);

        if (multiplier == -1)
            multiplier = Math.pow(1000, -3);
        else
            byteAmount = byteAmount.substring(0, byteAmount.length() -1).strip();

        try {
            return Double.parseDouble(byteAmount) * multiplier;
        }
        catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid byte amount '" + byteAmount +
                                               "': Must be a floating point number " +
                                               "optionally followed by k, M, G, T, P, E, Z or Y");
        }
    }

    private static NodeResources.DiskSpeed parseOptionalDiskSpeed(String diskSpeedString) {
        if (diskSpeedString == null) return NodeResources.DiskSpeed.getDefault();
        return switch (diskSpeedString) {
            case "fast" -> NodeResources.DiskSpeed.fast;
            case "slow" -> NodeResources.DiskSpeed.slow;
            case "any" -> NodeResources.DiskSpeed.any;
            default -> throw new IllegalArgumentException("Illegal disk-speed value '" + diskSpeedString +
                                                          "': Legal values are 'fast', 'slow' and 'any')");
        };
    }

    private static NodeResources.StorageType parseOptionalStorageType(String storageTypeString) {
        if (storageTypeString == null) return NodeResources.StorageType.getDefault();
        return switch (storageTypeString) {
            case "remote" -> NodeResources.StorageType.remote;
            case "local" -> NodeResources.StorageType.local;
            case "any" -> NodeResources.StorageType.any;
            default -> throw new IllegalArgumentException("Illegal storage-type value '" + storageTypeString +
                                                          "': Legal values are 'remote', 'local' and 'any')");
        };
    }

    private static NodeResources.Architecture parseOptionalArchitecture(String architecture) {
        if (architecture == null) return NodeResources.Architecture.getDefault();
        return switch (architecture) {
            case "x86_64" -> NodeResources.Architecture.x86_64;
            case "arm64" -> NodeResources.Architecture.arm64;
            case "any" -> NodeResources.Architecture.any;
            default -> throw new IllegalArgumentException("Illegal architecture value '" + architecture +
                                                          "': Legal values are 'x86_64', 'arm64' and 'any')");
        };
    }

    /**
     * Resolve any reference in nodesElement and return the referred element.
     *
     * If nodesElement does not refer to a different element, this method behaves as the identity function.
     */
    private static ModelElement resolveElement(ModelElement nodesElement) {
        var element = nodesElement.getXml();
        var referenceId = element.getAttribute("of");
        if (referenceId.isEmpty()) return nodesElement;

        var services = findParentByTag("services", element).orElseThrow(() -> clusterReferenceNotFoundException(referenceId));
        var referencedService = findChildById(services, referenceId).orElseThrow(() -> clusterReferenceNotFoundException(referenceId));
        if ( ! referencedService.getTagName().equals("content"))
            throw new IllegalArgumentException("service '" + referenceId + "' is not a content service");
        var referencedNodesElement = XML.getChild(referencedService, "nodes");
        if (referencedNodesElement == null)
            throw new IllegalArgumentException("expected reference to service '" + referenceId + "' to supply nodes, " +
                                               "but that service has no <nodes> element");

        return new ModelElement(referencedNodesElement);
    }

    /** Returns the ID of the parent container element of nodesElement, if any  */
    private static Optional<String> containerIdOf(ModelElement nodesElement) {
        var element = nodesElement.getXml();
        var container = findParentByTag("container", element);
        return container.map(el -> el.getAttribute("id"));
    }

    /** Returns the ID of the container element referencing nodesElement, if any */
    private static Optional<String> containerIdReferencing(ModelElement nodesElement) {
        var element = nodesElement.getXml();
        var services = findParentByTag("services", element);
        if (services.isEmpty()) return Optional.empty();

        var content = findParentByTag("content", element);
        if (content.isEmpty()) return Optional.empty();
        var contentClusterId = content.get().getAttribute("id");
        if (contentClusterId.isEmpty()) return Optional.empty();
        for (var rootChild : XML.getChildren(services.get())) {
            if ( ! ContainerModelBuilder.isContainerTag(rootChild)) continue;
            var nodes = XML.getChild(rootChild, "nodes");
            if (nodes == null) continue;
            if (!contentClusterId.equals(nodes.getAttribute("of"))) continue;
            return Optional.of(rootChild.getAttribute("id"));
        }
        return Optional.empty();
    }

    private static Optional<Element> findChildById(Element parent, String id) {
        for (Element child : XML.getChildren(parent))
            if (id.equals(child.getAttribute("id"))) return Optional.of(child);
        return Optional.empty();
    }

    private static Optional<Element> findParentByTag(String tag, Element element) {
        Node parent = element.getParentNode();
        if (parent == null) return Optional.empty();
        if ( ! (parent instanceof Element parentElement)) return Optional.empty();
        if (parentElement.getTagName().equals(tag)) return Optional.of(parentElement);
        return findParentByTag(tag, parentElement);
    }

    private static IllegalArgumentException clusterReferenceNotFoundException(String referenceId) {
        return new IllegalArgumentException("referenced service '" + referenceId + "' is not defined");
    }

    private static Optional<DockerImage> dockerImageToUse(ModelElement nodesElement, Optional<DockerImage> dockerImage) {
        String dockerImageFromElement = nodesElement.stringAttribute("docker-image");
        return dockerImageFromElement == null ? dockerImage : Optional.of(DockerImage.fromString(dockerImageFromElement));
    }

    /** Parses a value ("value") or value range ("[min-value, max-value]") */
    private static Pair<Double, Double> toRange(String name, ModelElement element, double defaultValue, ToDoubleFunction<String> valueParser) {
        String s = element.stringAttribute(name);
        try {
            Pair<Double, Double> pair;
            if (s == null) return new Pair<>(defaultValue, defaultValue);
            s = s.trim();
            if (s.startsWith("[") && s.endsWith("]")) {
                String[] numbers = s.substring(1, s.length() - 1).split(",");
                if (numbers.length != 2) throw new IllegalArgumentException();
                pair = new Pair<>(valueParser.applyAsDouble(numbers[0].trim()), valueParser.applyAsDouble(numbers[1].trim()));
                if (pair.getFirst() > pair.getSecond())
                    throw new IllegalArgumentException("first value cannot be larger than second value");
            } else {
                pair = new Pair<>(valueParser.applyAsDouble(s), valueParser.applyAsDouble(s));
            }
            if (pair.getFirst() < 0 || pair.getSecond() < 0)
                throw new IllegalArgumentException("values cannot be negative");
            return pair;
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expected a number or range on the form [min, max] for node resource '" + name + "', but got '" + s + "'", e);
        }
    }

    @Override
    public String toString() {
        return "specification of " + (dedicated ? "dedicated " : "") +
               (min.equals(max) ? min : "min " + min + " max " + max);
    }
}
