// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.builder.xml.dom.DomDistributorBuilder;
import com.yahoo.vespa.model.builder.xml.dom.DomStorageNodeBuilder;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.cluster.RedundancyBuilder;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;

/**
 * A group of storage nodes/distributors.
 *
 * @author unknown, probably thomasg
 * @author bratseth has done things here recently
 */
public class StorageGroup {

    private final boolean useCpuSocketAffinity;
    private final String index;
    private Optional<String> partitions;
    String name;
    private final boolean isHosted;
    private final Optional<Long> mmapNoCoreLimit;
    private final Optional<Boolean> coreOnOOM;
    private final Optional<String> noVespaMalloc;
    private final Optional<String> vespaMalloc;
    private final Optional<String> vespaMallocDebug;
    private final Optional<String> vespaMallocDebugStackTrace;

    private final List<StorageGroup> subgroups = new ArrayList<>();
    private final List<StorageNode> nodes = new ArrayList<>();

    /**
     * Creates a storage group
     *
     * @param isHosted true if this is in a hosted setup
     * @param name the name of this group
     * @param index the distribution-key index of this group
     * @param partitions the distribution strategy to use to distribute content to subgroups or empty
     *        (meaning that the "*" distribution will be used) only if this is a leaf group
     *        (having nodes, not subgroups as children).
     * @param useCpuSocketAffinity whether processes should be started with socket affinity
     */
    private StorageGroup(boolean isHosted, String name, String index, Optional<String> partitions,
                         boolean useCpuSocketAffinity, Optional<Long> mmapNoCoreLimit, Optional<Boolean> coreOnOOM,
                         Optional<String> noVespaMalloc, Optional<String> vespaMalloc,
                         Optional<String> vespaMallocDebug, Optional<String> vespaMallocDebugStackTrace)
    {
        this.isHosted = isHosted;
        this.index = index;
        this.name = name;
        this.partitions = partitions;
        this.useCpuSocketAffinity = useCpuSocketAffinity;
        this.mmapNoCoreLimit = mmapNoCoreLimit;
        this.coreOnOOM = coreOnOOM;
        this.noVespaMalloc = noVespaMalloc;
        this.vespaMalloc = vespaMalloc;
        this.vespaMallocDebug = vespaMallocDebug;
        this.vespaMallocDebugStackTrace = vespaMallocDebugStackTrace;
    }
    private StorageGroup(boolean isHosted, String name, String index) {
        this(isHosted, name, index, Optional.empty(), false, Optional.empty(),Optional.empty(), Optional.empty(),
             Optional.empty(), Optional.empty(), Optional.empty());
    }

    /** Returns the name of this group, or null if it is the root group */
    public String getName() { return name; }

    /** Returns the subgroups of this, or an empty list if it is a leaf group */
    public List<StorageGroup> getSubgroups() { return subgroups; }

    /** Returns the nodes of this, or an empty list of it is not a leaf group */
    public List<StorageNode> getNodes() { return nodes; }

    public boolean isHosted() { return isHosted; }

    /** Returns the index of this group, or null if it is the root group */
    public String getIndex() { return index; }

    public Optional<String> getPartitions() { return partitions; }
    public boolean useCpuSocketAffinity() { return useCpuSocketAffinity; }
    public Optional<Long> getMmapNoCoreLimit() { return mmapNoCoreLimit; }
    public Optional<Boolean> getCoreOnOOM() { return coreOnOOM; }
    public Optional<String> getNoVespaMalloc() { return noVespaMalloc; }
    public Optional<String> getVespaMalloc() { return vespaMalloc; }
    public Optional<String> getVespaMallocDebug() { return vespaMallocDebug; }
    public Optional<String> getVespaMallocDebugStackTrace() { return vespaMallocDebugStackTrace; }

    /** Returns all the nodes below this group */
    public List<StorageNode> recursiveGetNodes() {
        if ( ! nodes.isEmpty()) return nodes;
        List<StorageNode> nodes = new ArrayList<>();
        for (StorageGroup subgroup : subgroups)
            nodes.addAll(subgroup.recursiveGetNodes());
        return nodes;
    }

    public Collection<StorDistributionConfig.Group.Builder> getGroupStructureConfig() {
        List<StorDistributionConfig.Group.Builder> groups = new ArrayList<>();

        StorDistributionConfig.Group.Builder myGroup = new StorDistributionConfig.Group.Builder();
        getConfig(myGroup);
        groups.add(myGroup);

        for (StorageGroup g : subgroups) {
            groups.addAll(g.getGroupStructureConfig());
        }

        return groups;
    }

    public void getConfig(StorDistributionConfig.Group.Builder builder) {
        builder.index(index == null ? "invalid" : index);
        builder.name(name == null ? "invalid" : name);
        partitions.ifPresent(builder::partitions);
        for (StorageNode node : nodes) {
            StorDistributionConfig.Group.Nodes.Builder nb = new StorDistributionConfig.Group.Nodes.Builder();
            nb.index(node.getDistributionKey());
            nb.retired(node.isRetired());
            builder.nodes.add(nb);
        }
        builder.capacity(getCapacity());
    }

    public int getNumberOfLeafGroups() {
        if (subgroups.isEmpty()) return 1;
        int count = 0;
        for (StorageGroup g : subgroups)
            count += g.getNumberOfLeafGroups();
        return count;
    }

    public double getCapacity() {
        double capacity = 0;
        for (StorageNode node : nodes) {
            capacity += node.getCapacity();
        }
        for (StorageGroup group : subgroups) {
            capacity += group.getCapacity();
        }
        return capacity;
    }

    /** Returns the total number of nodes below this group */
    public int countNodes(boolean includeRetired) {
        int nodeCount = (int)nodes.stream().filter(node -> includeRetired || ! node.isRetired()).count();
        for (StorageGroup group : subgroups)
            nodeCount += group.countNodes(includeRetired);
        return nodeCount;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof StorageGroup other) {
            return this.index.equals(other.index) &&
                   this.name.equals(other.name) &&
                   this.partitions.equals(other.partitions);
        }
        return false;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(index, name, partitions);
    }

    /**
     * Builds the root storage group of a content cluster, with its subgroups and nodes.
     */
    public static class Builder {

        private final ModelElement clusterElement;
        private final DeployState deployState;

        /** The root {@code <group>} and {@code <nodes>} elements of the cluster: at most one of these are present. */
        private final Optional<ModelElement> groupElement;
        private final Optional<ModelElement> nodesElement;

        /** The root group, which is without subgroups and nodes until {@link #build} is called. */
        private final StorageGroup rootGroup;

        /** The cluster to allocate hosts to: present if and only if hosted. */
        private final Optional<ClusterSpec> clusterSpec;

        /**
         * Validates the given content cluster as much as possible, and parses the element enough to determine
         * the allocation requests (ClusterSpec) needed for early provisioning. During model building, this builder
         * can be used to complete the building ({@link #build(ContentCluster, Boolean) build()}).
         *
         * @throws IllegalArgumentException if the cluster is invalid
         */
        public static Builder from(ModelElement clusterElement, ConfigModelContext context) {
            try {
                DeployState deployState = context.getDeployState();
                if (deployState.isHosted())
                    validateRedundancyAndGroups(clusterElement, context);

                Optional<ModelElement> groupElement = Optional.ofNullable(clusterElement.child("group"));
                Optional<ModelElement> nodesElement = getNodes(clusterElement);

                if (groupElement.isPresent() && nodesElement.isPresent())
                    throw new IllegalArgumentException("Both <group> and <nodes> is specified: Only one of these tags can be used in the same configuration");

                StorageGroup rootGroup = createGroup(deployState.isHosted(), groupElement, null, null);

                final Optional<ClusterSpec> clusterSpec;
                if (deployState.isHosted()) {
                    if (groupElement.isPresent() && ! groupElement.get().subElements("group").isEmpty())
                        throw new IllegalArgumentException("Specifying individual groups is not supported on hosted applications");

                    var clusterId = ClusterSpec.Id.from(ContentCluster.getClusterId(clusterElement));
                    clusterSpec = Optional.of(hostedNodesSpecification(nodesElement, context)
                                                      .setClusterSpecFrom(ClusterSpec.Type.content, clusterId, true, context.clusterInfo().build())
                                                      .cluster());
                } else {
                    clusterSpec = Optional.empty();
                }

                return new Builder(clusterElement, deployState, groupElement, nodesElement, rootGroup, clusterSpec);
            }
            catch (IllegalArgumentException e) {
                // Same message as in build(), see ContentCluster.toString()
                throw new IllegalArgumentException("In content cluster '" + ContentCluster.getClusterId(clusterElement) + "'", e);
            }
        }

        private Builder(ModelElement clusterElement, DeployState deployState,
                        Optional<ModelElement> groupElement, Optional<ModelElement> nodesElement,
                        StorageGroup rootGroup, Optional<ClusterSpec> clusterSpec) {
            this.clusterElement = clusterElement;
            this.deployState = deployState;
            this.groupElement = groupElement;
            this.nodesElement = nodesElement;
            this.rootGroup = rootGroup;
            this.clusterSpec = clusterSpec;
        }

        /** Returns the clusters to allocate hosts to for this content cluster: exactly one if hosted, otherwise none. */
        public List<ClusterSpec> collectAllocationRequests() {
            return clusterSpec.stream().toList();
        }

        /**
         * Allocates hosts to the cluster returned by {@link #collectAllocationRequests}, if any, builds the subgroups
         * and nodes of the root group, and sets the redundancy of the owner. This can only be called once.
         */
        public StorageGroup build(ContentCluster owner, Boolean isStreaming) {
            try {
                if (groupElement.isPresent() && groupElement.get().integerAttribute("distribution-key") != null) {
                    deployState.getDeployLogger().logApplicationPackage(Level.INFO, "'distribution-key' attribute on a content cluster's root group is ignored");
                }

                if (clusterSpec.isPresent())
                    addAllocatedNodes(clusterSpec.get(), owner);
                else
                    addExplicitNodes(owner);

                RedundancyBuilder redundancyBuilder = new RedundancyBuilder(clusterElement);
                Redundancy redundancy = redundancyBuilder.build(owner.isHosted(), isStreaming, rootGroup.subgroups.size(),
                                                                rootGroup.getNumberOfLeafGroups(), rootGroup.countNodes(false));
                owner.setRedundancy(redundancy);
                if (rootGroup.partitions.isEmpty() && (redundancy.groups() > 1)) {
                    rootGroup.partitions = Optional.of(computePartitions(redundancy.finalRedundancy(), redundancy.groups()));
                }
                return rootGroup;
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("In " + owner, e);
            }
        }

        private static void validateRedundancyAndGroups(ModelElement clusterElement, ConfigModelContext context) {
            var redundancyElement = clusterElement.child("redundancy");
            if (redundancyElement == null) return;
            long redundancy = redundancyElement.asLong();

            var nodesElement = clusterElement.child("nodes");
            if (nodesElement == null) return;
            var nodesSpec = NodesSpecification.from(nodesElement, context);

            // Allow dev deployment of self-hosted apps which do not specify node count by defaulting to 1 node
            if (!nodesSpec.specifiesNodeCount() && context.getDeployState().zone().environment() == Environment.dev) return;

            int minNodesPerGroup = (int) Math.ceil((double) nodesSpec.minResources().nodes() / nodesSpec.minResources().groups());

            if (minNodesPerGroup < redundancy) {
                throw new IllegalArgumentException("This cluster specifies redundancy " + redundancy +
                                                   ", but this cannot be higher than " +
                                                   "the minimum nodes per group, which is " + minNodesPerGroup);
            }
        }

        /** This returns a partition string which specifies equal distribution between all groups */
        // TODO: Make a partitions object
        static private String computePartitions(int redundancyPerGroup, int numGroups) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < numGroups - 1; ++i) {
                sb.append(redundancyPerGroup);
                sb.append("|");
            }
            sb.append("*");
            return sb.toString();
        }

        /**
         * Returns a new group without subgroups and nodes.
         *
         * @param groupElement the element specifying the group, or empty if this is an implicit root group
         * @param name the name of the group, or null if this is the root group
         * @param index the index of the group, or null if this is the root group
         */
        private static StorageGroup createGroup(boolean isHosted, Optional<ModelElement> groupElement, String name, String index) {
            return new StorageGroup(
                    isHosted, name, index,
                    childAsString(groupElement, "distribution.partitions"),
                    booleanAttributeOr(groupElement, VespaDomBuilder.CPU_SOCKET_AFFINITY_ATTRIB_NAME, false),
                    childAsLong(groupElement, VespaDomBuilder.MMAP_NOCORE_LIMIT),
                    childAsBoolean(groupElement, VespaDomBuilder.CORE_ON_OOM),
                    childAsString(groupElement, VespaDomBuilder.NO_VESPAMALLOC),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC_DEBUG),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC_DEBUG_STACKTRACE));
        }

        /**
         * Hosted: Allocates hosts to the given cluster, and adds a node for each to the root group,
         * or to a subgroup for each group of the allocation if there are more than one.
         *
         * Note: DO NOT change allocation behaviour to allow version X and Y of the config-model to allocate a different
         * set of nodes. Such changes must be guarded by a common condition (e.g. feature flag) so the behaviour can be
         * changed simultaneously for all active config models.
         */
        private void addAllocatedNodes(ClusterSpec cluster, ContentCluster owner) {
            Map<HostResource, ClusterMembership> hostMapping = owner.getRoot().hostSystem().allocateHosts(cluster, deployState);
            Map<Integer, Map<HostResource, ClusterMembership>> hostGroups = collectAllocatedSubgroups(hostMapping);
            if (hostGroups.size() > 1) {
                // create subgroups as returned from allocation
                for (Map.Entry<Integer, Map<HostResource, ClusterMembership>> hostGroup : hostGroups.entrySet()) {
                    String groupIndex = String.valueOf(hostGroup.getKey());
                    StorageGroup subgroup = new StorageGroup(true, groupIndex, groupIndex);
                    for (Map.Entry<HostResource, ClusterMembership> host : hostGroup.getValue().entrySet()) {
                        subgroup.nodes.add(createStorageNode(owner, host.getKey(), subgroup, host.getValue()));
                    }
                    rootGroup.subgroups.add(subgroup);
                }
            }
            else { // or otherwise just create the nodes directly on the root group
                for (Map.Entry<HostResource, ClusterMembership> host : hostMapping.entrySet()) {
                    rootGroup.nodes.add(createStorageNode(owner, host.getKey(), rootGroup, host.getValue()));
                }
            }
        }

        /** Collect hosts per group */
        private static Map<Integer, Map<HostResource, ClusterMembership>> collectAllocatedSubgroups(Map<HostResource, ClusterMembership> hostMapping) {
            Map<Integer, Map<HostResource, ClusterMembership>> hostsPerGroup = new LinkedHashMap<>();
            for (Map.Entry<HostResource, ClusterMembership> entry : hostMapping.entrySet()) {
                Integer group = entry.getValue().group();
                Map<HostResource, ClusterMembership> hostsInGroup = hostsPerGroup.computeIfAbsent(group, k -> new LinkedHashMap<>());
                hostsInGroup.put(entry.getKey(), entry.getValue());
            }
            return hostsPerGroup;
        }

        private StorageNode createStorageNode(ContentCluster parent, HostResource hostResource, StorageGroup parentGroup, ClusterMembership clusterMembership) {
            StorageNode sNode = new StorageNode(deployState.getProperties(), parent.getStorageCluster(), null, clusterMembership.index(), clusterMembership.retired());
            sNode.setHostResource(hostResource);
            sNode.initService(deployState);

            parent.getSearch().addSearchNode(deployState, sNode, parentGroup);
            PersistenceEngine provider = parent.getPersistence().create(sNode);
            Distributor d = new Distributor(deployState.getProperties(), parent.getDistributorNodes(), clusterMembership.index(), provider);
            d.setHostResource(sNode.getHostResource());
            d.initService(deployState);
            return sNode;
        }

        /**
         * Self-hosted: Adds the explicitly specified subgroups and nodes to the root group,
         * or a single node if there are none.
         */
        private void addExplicitNodes(ContentCluster owner) {
            addExplicitNodes(rootGroup, groupElement, nodesElement, owner);

            if (rootGroup.subgroups.isEmpty() && rootGroup.nodes.isEmpty()) // no nodes or groups: create single node
                rootGroup.nodes.add(buildSingleNode(owner));
        }

        /**
         * Adds the explicitly specified subgroups and nodes to the given group, recursively.
         * These are the possibilities:
         * <ul>
         * <li>group and nodes is present: This is a leaf group specifying a set of nodes</li>
         * <li>only group is present: This is a nonleaf group, or a leaf group with nodes directly below it</li>
         * <li>only nodes is present: This is the implicitly specified toplevel leaf group</li>
         * <li>Neither element is present: Nothing is added</li>
         * </ul>
         */
        private void addExplicitNodes(StorageGroup group, Optional<ModelElement> groupElement, Optional<ModelElement> nodesElement,
                                      ContentCluster owner) {
            List<ModelElement> subGroupElements = groupElement.map(element -> element.subElements("group")).orElseGet(List::of);
            if (subGroupElements.size() > 1 && group.getPartitions().isEmpty())
                throw new IllegalArgumentException("'distribution' attribute is required with multiple subgroups");

            String indexPrefix = group.index == null ? "" : group.index + ".";
            for (ModelElement subGroupElement : subGroupElements) {
                StorageGroup subgroup = createGroup(false, Optional.of(subGroupElement), subGroupElement.stringAttribute("name"),
                                                    indexPrefix + subGroupElement.integerAttribute("distribution-key"));
                addExplicitNodes(subgroup, Optional.of(subGroupElement), Optional.ofNullable(subGroupElement.child("nodes")), owner);
                group.subgroups.add(subgroup);
            }

            if ( ! subGroupElements.isEmpty() && nodesElement.isPresent())
                throw new IllegalArgumentException("A group can contain either explicit subgroups or a nodes specification, but not both.");

            for (ModelElement nodeElement : nodeElements(groupElement))
                group.nodes.add(buildNode(nodeElement, owner, group));
            for (ModelElement nodeElement : nodeElements(nodesElement))
                group.nodes.add(buildNode(nodeElement, owner, group));
        }

        private static List<ModelElement> nodeElements(Optional<ModelElement> groupOrNodesElement) {
            return groupOrNodesElement.map(element -> element.subElements("node")).orElseGet(List::of);
        }

        private StorageNode buildNode(ModelElement nodeElement, ContentCluster parent, StorageGroup storageGroup) {
            StorageNode sNode = new DomStorageNodeBuilder().build(deployState, parent.getStorageCluster(), nodeElement.getXml());
            parent.getSearch().addSearchNode(deployState, sNode, storageGroup, nodeElement);
            PersistenceEngine provider = parent.getPersistence().create(sNode);
            new DomDistributorBuilder(provider).build(deployState, parent.getDistributorNodes(), nodeElement.getXml());
            return sNode;
        }

        private StorageNode buildSingleNode(ContentCluster parent) {
            int distributionKey = 0;

            StorageNode storageNode = new StorageNode(deployState.getProperties(), parent.getStorageCluster(), 1.0, distributionKey , false);
            storageNode.setHostResource(parent.hostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC));

            parent.getSearch().addSearchNode(deployState, storageNode, rootGroup);
            PersistenceEngine provider = parent.getPersistence().create(storageNode);
            storageNode.initService(deployState);

            Distributor distributor = new Distributor(deployState.getProperties(), parent.getDistributorNodes(), distributionKey, provider);
            distributor.setHostResource(storageNode.getHostResource());
            distributor.initService(deployState);
            return storageNode;
        }

        /** Returns the nodes to allocate to this cluster in hosted. */
        private static NodesSpecification hostedNodesSpecification(Optional<ModelElement> nodesElement, ConfigModelContext context) {
            if (nodesElement.isEmpty())
                return NodesSpecification.nonDedicated(1, context);

            ModelElement nodes = nodesElement.get();
            if (hasCountAttribute(nodes) || hasGroupAttributes(nodes) ||
                context.getDeployState().zone().environment().isManuallyDeployed())
                return NodesSpecification.from(nodes, context);

            throw new IllegalArgumentException("""
                    Clusters in hosted environments must have a <nodes count='N'> tag
                    matching all zones, and having no <node> subtags,
                    or <nodes groups='N' and group-size='N'> tags
                    see https://docs.vespa.ai/en/reference/applications/services/services.html#nodes""");
        }

        private static boolean hasCountAttribute(ModelElement nodes) {
            return nodes.stringAttribute("count") != null;
        }

        private static boolean hasGroupAttributes(ModelElement nodes) {
            return nodes.stringAttribute("groups") != null && nodes.stringAttribute("group-size") != null;
        }

        private static Optional<String> childAsString(Optional<ModelElement> element, String childTagName) {
            return element.map(modelElement -> modelElement.childAsString(childTagName));
        }
        private static Optional<Long> childAsLong(Optional<ModelElement> element, String childTagName) {
            return element.map(modelElement -> modelElement.childAsLong(childTagName));
        }
        private static Optional<Boolean> childAsBoolean(Optional<ModelElement> element, String childTagName) {
            return element.map(modelElement -> modelElement.childAsBoolean(childTagName));
        }

        private static boolean booleanAttributeOr(Optional<ModelElement> element, String attributeName, boolean defaultValue) {
            return element.map(modelElement -> modelElement.booleanAttribute(attributeName, defaultValue)).orElse(defaultValue);
        }

        private static Optional<ModelElement> getNodes(ModelElement groupOrNodesElement) {
            if (groupOrNodesElement.getXml().getTagName().equals("nodes")) return Optional.of(groupOrNodesElement);
            return Optional.ofNullable(groupOrNodesElement.child("nodes"));
        }

    }

}
