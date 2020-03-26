// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.collections.Pair;
import com.yahoo.component.Version;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.provision.Capacity;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterResources;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.text.XML;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * A common utility class to represent a requirement for nodes during model building.
 * Such a requirement is commonly specified in services.xml as a <code>nodes</code> element.
 *
 * @author bratseth
 */
public class NodesSpecification {

    private final ClusterResources min, max;

    private final boolean dedicated;

    /** The Vespa version we want the nodes to run */
    private Version version;

    /** 
     * Whether the capacity amount specified is required or can it be relaxed 
     * at the discretion of the component fulfilling it
     */
    private final boolean required;

    private final boolean canFail;

    private final boolean exclusive;

    /** The repo part of a docker image (without tag), optional */
    private final Optional<String> dockerImageRepo;

    /** The ID of the cluster referencing this node specification, if any */
    private final Optional<String> combinedId;

    private NodesSpecification(ClusterResources min,
                               ClusterResources max,
                               boolean dedicated, Version version,
                               boolean required, boolean canFail, boolean exclusive,
                               Optional<String> dockerImageRepo,
                               Optional<String> combinedId) {
        this.min = min;
        this.max = max;
        this.dedicated = dedicated;
        this.version = version;
        this.required = required;
        this.canFail = canFail;
        this.exclusive = exclusive;
        this.dockerImageRepo = dockerImageRepo;
        this.combinedId = combinedId;
    }

    private static NodesSpecification create(boolean dedicated, boolean canFail, Version version,
                                             ModelElement nodesElement, Optional<String> dockerImageRepo) {
        var resolvedElement = resolveElement(nodesElement);
        var combinedId = findCombinedId(nodesElement, resolvedElement);
        var resources = toResources(resolvedElement);
        return new NodesSpecification(resources.getFirst(),
                                      resources.getSecond(),
                                      dedicated,
                                      version,
                                      resolvedElement.booleanAttribute("required", false),
                                      canFail,
                                      resolvedElement.booleanAttribute("exclusive", false),
                                      dockerImageToUse(resolvedElement, dockerImageRepo),
                                      combinedId);
    }

    private static Pair<ClusterResources, ClusterResources> toResources(ModelElement nodesElement) {
        Pair<Integer, Integer> nodes =  toRange(nodesElement.stringAttribute("count"),  1, Integer::parseInt);
        Pair<Integer, Integer> groups = toRange(nodesElement.stringAttribute("groups"), 1, Integer::parseInt);
        var min = new ClusterResources(nodes.getFirst(),  groups.getFirst(),  nodeResources(nodesElement).getFirst());
        var max = new ClusterResources(nodes.getSecond(), groups.getSecond(), nodeResources(nodesElement).getSecond());
        return new Pair<>(min, max);
    }

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
                      context.getDeployState().getWantedDockerImageRepo());
    }

    /**
     * Returns a requirement for dedicated nodes taken from the <code>nodes</code> element
     * contained in the given parent element, or empty if the parent element is null, or the nodes elements
     * is not present.
     */
    public static Optional<NodesSpecification> fromParent(ModelElement parentElement, ConfigModelContext context) {
        if (parentElement == null) return Optional.empty();
        ModelElement nodesElement = parentElement.child("nodes");
        if (nodesElement == null) return Optional.empty();
        return Optional.of(from(nodesElement, context));
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
                                  context.getDeployState().getWantedDockerImageRepo()));
    }

    /**
     * Returns a requirement from <code>count</code> non-dedicated nodes in one group
     */
    public static NodesSpecification nonDedicated(int count, ConfigModelContext context) {
        return new NodesSpecification(new ClusterResources(count, 1, NodeResources.unspecified),
                                      new ClusterResources(count, 1, NodeResources.unspecified),
                                      false,
                                      context.getDeployState().getWantedNodeVespaVersion(),
                                      false,
                                      ! context.getDeployState().getProperties().isBootstrap(),
                                      false,
                                      context.getDeployState().getWantedDockerImageRepo(),
                                      Optional.empty());
    }

    /** Returns a requirement from <code>count</code> dedicated nodes in one group */
    public static NodesSpecification dedicated(int count, ConfigModelContext context) {
        return new NodesSpecification(new ClusterResources(count, 1, NodeResources.unspecified),
                                      new ClusterResources(count, 1, NodeResources.unspecified),
                                      true,
                                      context.getDeployState().getWantedNodeVespaVersion(),
                                      false,
                                      ! context.getDeployState().getProperties().isBootstrap(),
                                      false,
                                      context.getDeployState().getWantedDockerImageRepo(),
                                      Optional.empty());
    }

    public ClusterResources minResources() { return min; }
    public ClusterResources maxResources() { return max; }

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

    public Map<HostResource, ClusterMembership> provision(HostSystem hostSystem,
                                                          ClusterSpec.Type clusterType,
                                                          ClusterSpec.Id clusterId,
                                                          DeployLogger logger) {
        if (combinedId.isPresent())
            clusterType = ClusterSpec.Type.combined;
        ClusterSpec cluster = ClusterSpec.request(clusterType, clusterId)
                .vespaVersion(version)
                .exclusive(exclusive)
                .combinedId(combinedId.map(ClusterSpec.Id::from))
                .dockerImageRepo(dockerImageRepo)
                .build();
        return hostSystem.allocateHosts(cluster, Capacity.from(min, max, required, canFail), logger);
    }

    private static Pair<NodeResources, NodeResources> nodeResources(ModelElement nodesElement) {
        ModelElement resources = nodesElement.child("resources");
        if (resources != null) {
            return nodeResourcesFromResorcesElement(resources);
        }
        else if (nodesElement.stringAttribute("flavor") != null) { // legacy fallback
            var flavorResources = NodeResources.fromLegacyName(nodesElement.stringAttribute("flavor"));
            return new Pair<>(flavorResources, flavorResources);
        }
        else {
            return new Pair<>(NodeResources.unspecified, NodeResources.unspecified);
        }
    }

    private static Pair<NodeResources, NodeResources> nodeResourcesFromResorcesElement(ModelElement element) {
        Pair<Double, Double> vcpu       = toRange(element.requiredStringAttribute("vcpu"),   .0, Double::parseDouble);
        Pair<Double, Double> memory     = toRange(element.requiredStringAttribute("memory"), .0, s -> parseGbAmount(s, "B"));
        Pair<Double, Double> disk       = toRange(element.requiredStringAttribute("disk"),   .0, s -> parseGbAmount(s, "B"));
        Pair<Double, Double> bandwith   = toRange(element.stringAttribute("bandwith"),       .3, s -> parseGbAmount(s, "BPS"));
        NodeResources.DiskSpeed   diskSpeed   = parseOptionalDiskSpeed(element.stringAttribute("disk-speed"));
        NodeResources.StorageType storageType = parseOptionalStorageType(element.stringAttribute("storage-type"));

        var min = new NodeResources(vcpu.getFirst(),  memory.getFirst(),  disk.getFirst(),  bandwith.getFirst(),  diskSpeed, storageType);
        var max = new NodeResources(vcpu.getSecond(), memory.getSecond(), disk.getSecond(), bandwith.getSecond(), diskSpeed, storageType);
        return new Pair<>(min, max);
    }

    private static double parseGbAmount(String byteAmount, String unit) {
        byteAmount = byteAmount.strip();
        byteAmount = byteAmount.toUpperCase();
        if (byteAmount.endsWith(unit))
            byteAmount = byteAmount.substring(0, byteAmount.length() - unit.length());

        double multiplier = Math.pow(1000, -3);
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

        byteAmount = byteAmount.substring(0, byteAmount.length() -1 ).strip();
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
        switch (diskSpeedString) {
            case "fast" : return NodeResources.DiskSpeed.fast;
            case "slow" : return NodeResources.DiskSpeed.slow;
            case "any"  : return NodeResources.DiskSpeed.any;
            default: throw new IllegalArgumentException("Illegal disk-speed value '" + diskSpeedString +
                                                        "': Legal values are 'fast', 'slow' and 'any')");
        }
    }

    private static NodeResources.StorageType parseOptionalStorageType(String storageTypeString) {
        if (storageTypeString == null) return NodeResources.StorageType.getDefault();
        switch (storageTypeString) {
            case "remote" : return NodeResources.StorageType.remote;
            case "local"  : return NodeResources.StorageType.local;
            case "any"    : return NodeResources.StorageType.any;
            default: throw new IllegalArgumentException("Illegal storage-type value '" + storageTypeString +
                                                        "': Legal values are 'remote', 'local' and 'any')");
        }
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
        for (var containerTag : List.of("container", "jdisc")) {
            var container = findParentByTag(containerTag, element);
            if (container.isEmpty()) continue;
            return container.map(el -> el.getAttribute("id"));
        }
        return Optional.empty();
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
        if ( ! (parent instanceof Element)) return Optional.empty();
        Element parentElement = (Element) parent;
        if (parentElement.getTagName().equals(tag)) return Optional.of(parentElement);
        return findParentByTag(tag, parentElement);
    }

    private static IllegalArgumentException clusterReferenceNotFoundException(String referenceId) {
        return new IllegalArgumentException("referenced service '" + referenceId + "' is not defined");
    }

    private static Optional<String> dockerImageToUse(ModelElement nodesElement, Optional<String> dockerImage) {
        String dockerImageFromElement = nodesElement.stringAttribute("docker-image");
        return dockerImageFromElement == null ? dockerImage : Optional.of(dockerImageFromElement);
    }

    /** Parses a value ("value") or value range ("[min-value, max-value]") */
    private static <T> Pair<T, T> toRange(String s, T defaultValue, Function<String, T> valueParser) {
        try {
            if (s == null) return new Pair<>(defaultValue, defaultValue);
            s = s.trim();
            if (s.startsWith("[") && s.endsWith("]")) {
                String[] numbers = s.substring(1, s.length() - 1).split(",");
                if (numbers.length != 2) throw new IllegalArgumentException();
                return new Pair<>(valueParser.apply(numbers[0].trim()), valueParser.apply(numbers[1].trim()));
            } else {
                return new Pair<>(valueParser.apply(s), valueParser.apply(s));
            }
        }
        catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Expected a number or range on the form [min, max], but got '" + s + "'");
        }
    }

    @Override
    public String toString() {
        return "specification of " + (dedicated ? "dedicated " : "") +
               (min.equals(max) ? min : "min " + min + " max " + max);
    }

}
