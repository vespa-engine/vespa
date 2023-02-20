// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.HostSystem;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.content.cluster.RedundancyBuilder;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
        if (partitions.isPresent())
            builder.partitions(partitions.get());
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
    public boolean equals(Object obj) {
        if (obj instanceof StorageGroup) {
            StorageGroup rhs = (StorageGroup)obj;
            return this.index.equals(rhs.index) &&
                    this.name.equals(rhs.name) &&
                    this.partitions.equals(rhs.partitions);
        }
        return false;
    }

    @Override public int hashCode() {
        return java.util.Objects.hash(index, name, partitions);
    }

    public static Map<HostResource, ClusterMembership> provisionHosts(NodesSpecification nodesSpecification, 
                                                                      String clusterIdString, 
                                                                      HostSystem hostSystem, 
                                                                      DeployLogger logger) {
        ClusterSpec.Id clusterId = ClusterSpec.Id.from(clusterIdString);
        return nodesSpecification.provision(hostSystem, ClusterSpec.Type.content, clusterId, logger, true);
    }

    public static class Builder {

        private final ModelElement clusterElement;
        private final ConfigModelContext context;

        public Builder(ModelElement clusterElement, ConfigModelContext context) {
            this.clusterElement = clusterElement;
            this.context = context;
        }

        public StorageGroup buildRootGroup(DeployState deployState, RedundancyBuilder redundancyBuilder, ContentCluster owner) {
            try {
                if (owner.isHosted())
                    validateRedundancyAndGroups(deployState.zone().environment());

                Optional<ModelElement> group = Optional.ofNullable(clusterElement.child("group"));
                Optional<ModelElement> nodes = getNodes(clusterElement);

                if (group.isPresent() && nodes.isPresent())
                    throw new IllegalArgumentException("Both <group> and <nodes> is specified: Only one of these tags can be used in the same configuration");
                if (group.isPresent() && (group.get().integerAttribute("distribution-key") != null)) {
                    deployState.getDeployLogger().logApplicationPackage(Level.INFO, "'distribution-key' attribute on a content cluster's root group is ignored");
                }

                GroupBuilder groupBuilder = collectGroup(owner.isHosted(), group, nodes, null, null);
                StorageGroup storageGroup = owner.isHosted()
                                            ? groupBuilder.buildHosted(deployState, owner, Optional.empty())
                                            : groupBuilder.buildNonHosted(deployState, owner, Optional.empty());

                Redundancy redundancy = redundancyBuilder.build(owner.isHosted(), storageGroup.subgroups.size(),
                                                                storageGroup.getNumberOfLeafGroups(), storageGroup.countNodes(false));
                owner.setRedundancy(redundancy);
                if (storageGroup.partitions.isEmpty() && (redundancy.groups() > 1)) {
                    storageGroup.partitions = Optional.of(computePartitions(redundancy.finalRedundancy(), redundancy.groups()));
                }
                return storageGroup;
            }
            catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("In " + owner, e);
            }
        }

        private void validateRedundancyAndGroups(Environment environment) {
            var redundancyElement = clusterElement.child("redundancy");
            if (redundancyElement == null) return;
            long redundancy = redundancyElement.asLong();

            var nodesElement = clusterElement.child("nodes");
            if (nodesElement == null) return;
            var nodesSpec = NodesSpecification.from(nodesElement, context);

            // Allow dev deployment of self-hosted app (w/o count attribute): absent count => 1 node
            if (!nodesSpec.hasCountAttribute() && environment == Environment.dev) return;

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
         * Represents a storage group and can build storage nodes in both hosted and non-hosted environments.
         */
        private static class GroupBuilder {

            private final StorageGroup storageGroup;

            /* The explicitly defined subgroups of this */
            private final List<GroupBuilder> subGroups;
            private final List<XmlNodeBuilder> nodeBuilders;

            /** The nodes explicitly specified as a nodes tag in this group, or empty if none */
            private final Optional<NodesSpecification> nodeRequirement;


            private GroupBuilder(StorageGroup storageGroup, List<GroupBuilder> subGroups, List<XmlNodeBuilder> nodeBuilders,
                                 Optional<NodesSpecification> nodeRequirement) {
                this.storageGroup = storageGroup;
                this.subGroups = subGroups;
                this.nodeBuilders = nodeBuilders;
                this.nodeRequirement = nodeRequirement;
            }

            /**
             * Builds a storage group for a nonhosted environment
             *
             * @param owner the cluster owning this
             * @param parent the parent storage group, or empty if this is the root group
             * @return the storage group build by this
             */
            public StorageGroup buildNonHosted(DeployState deployState, ContentCluster owner, Optional<GroupBuilder> parent) {
                for (GroupBuilder subGroup : subGroups) {
                    storageGroup.subgroups.add(subGroup.buildNonHosted(deployState, owner, Optional.of(this)));
                }
                for (XmlNodeBuilder nodeBuilder : nodeBuilders) {
                    storageGroup.nodes.add(nodeBuilder.build(deployState, owner, storageGroup));
                }
                
                if (parent.isEmpty() && subGroups.isEmpty() && nodeBuilders.isEmpty()) { // no nodes or groups: create single node
                    storageGroup.nodes.add(buildSingleNode(deployState, owner));
                }

                return storageGroup;
            }

            private StorageNode buildSingleNode(DeployState deployState, ContentCluster parent) {
                int distributionKey = 0;

                StorageNode searchNode = new StorageNode(deployState.getProperties(), parent.getStorageCluster(), 1.0, distributionKey , false);
                searchNode.setHostResource(parent.hostSystem().getHost(Container.SINGLENODE_CONTAINER_SERVICESPEC));
                PersistenceEngine provider = parent.getPersistence().create(deployState, searchNode, storageGroup, null);
                searchNode.initService(deployState);

                Distributor distributor = new Distributor(deployState.getProperties(), parent.getDistributorNodes(), distributionKey, null, provider);
                distributor.setHostResource(searchNode.getHostResource());
                distributor.initService(deployState);
                return searchNode;
            }
            
            /**
             * Builds a storage group for a hosted environment
             *
             * @param owner the cluster owning this
             * @param parent the parent storage group, or empty if this is the root group
             * @return the storage group build by this
             */
            public StorageGroup buildHosted(DeployState deployState, ContentCluster owner, Optional<GroupBuilder> parent) {
                if (storageGroup.getIndex() != null)
                    throw new IllegalArgumentException("Specifying individual groups is not supported on hosted applications");
                Map<HostResource, ClusterMembership> hostMapping =
                        nodeRequirement.isPresent() ?
                        provisionHosts(nodeRequirement.get(), owner.getStorageCluster().getClusterName(), owner.getRoot().hostSystem(), deployState.getDeployLogger()) :
                        Collections.emptyMap();

                Map<Optional<ClusterSpec.Group>, Map<HostResource, ClusterMembership>> hostGroups = collectAllocatedSubgroups(hostMapping);
                if (hostGroups.size() > 1) {
                    if (parent.isPresent())
                        throw new IllegalArgumentException("Cannot specify groups using the groups attribute in nested content groups");

                    // create subgroups as returned from allocation
                    for (Map.Entry<Optional<ClusterSpec.Group>, Map<HostResource, ClusterMembership>> hostGroup : hostGroups.entrySet()) {
                        String groupIndex = String.valueOf(hostGroup.getKey().get().index());
                        StorageGroup subgroup = new StorageGroup(true, groupIndex, groupIndex);
                        for (Map.Entry<HostResource, ClusterMembership> host : hostGroup.getValue().entrySet()) {
                            subgroup.nodes.add(createStorageNode(deployState, owner, host.getKey(), subgroup, host.getValue()));
                        }
                        storageGroup.subgroups.add(subgroup);
                    }
                }
                else { // or otherwise just create the nodes directly on this group, or the explicitly enumerated subgroups
                    for (Map.Entry<HostResource, ClusterMembership> host : hostMapping.entrySet()) {
                        storageGroup.nodes.add(createStorageNode(deployState, owner, host.getKey(), storageGroup, host.getValue()));
                    }
                    for (GroupBuilder subGroup : subGroups) {
                        storageGroup.subgroups.add(subGroup.buildHosted(deployState, owner, Optional.of(this)));
                    }
                }
                return storageGroup;
            }

            /** Collect hosts per group */
            private Map<Optional<ClusterSpec.Group>, Map<HostResource, ClusterMembership>> collectAllocatedSubgroups(Map<HostResource, ClusterMembership> hostMapping) {
                Map<Optional<ClusterSpec.Group>, Map<HostResource, ClusterMembership>> hostsPerGroup = new LinkedHashMap<>();
                for (Map.Entry<HostResource, ClusterMembership> entry : hostMapping.entrySet()) {
                    Optional<ClusterSpec.Group> group = entry.getValue().cluster().group();
                    Map<HostResource, ClusterMembership> hostsInGroup = hostsPerGroup.get(group);
                    if (hostsInGroup == null) {
                        hostsInGroup = new LinkedHashMap<>();
                        hostsPerGroup.put(group, hostsInGroup);
                    }
                    hostsInGroup.put(entry.getKey(), entry.getValue());
                }
                return hostsPerGroup;
            }

        }

        private static class XmlNodeBuilder {

            private final ModelElement clusterElement;
            private final ModelElement element;

            private XmlNodeBuilder(ModelElement clusterElement, ModelElement element) {
                this.clusterElement = clusterElement;
                this.element = element;
            }

            public StorageNode build(DeployState deployState, ContentCluster parent, StorageGroup storageGroup) {
                StorageNode sNode = new StorageNode.Builder().build(deployState, parent.getStorageCluster(), element.getXml());
                PersistenceEngine provider = parent.getPersistence().create(deployState, sNode, storageGroup, element);
                new Distributor.Builder(clusterElement, provider).build(deployState, parent.getDistributorNodes(), element.getXml());
                return sNode;
            }
        }

        /**
         * Creates a content group builder from a group and/or nodes element.
         * These are the possibilities:
         * <ul>
         * <li>group and nodes is present: This is a leaf group specifying a set of nodes</li>
         * <li>only group is present: This is a nonleaf group</li>
         * <li>only nodes is present: This is the implicitly specified toplevel leaf group, or a set of groups
         *                            specified using a group count attribute.
         * <li>Neither element is present: Create a single node.
         * </ul>
         *
         * Note: DO NOT change allocation behaviour to allow version X and Y of the config-model to allocate a different
         * set of nodes. Such changes must be guarded by a common condition (e.g. feature flag) so the behaviour can be
         * changed simultaneously for all active config models.
         */
        private GroupBuilder collectGroup(boolean isHosted, Optional<ModelElement> groupElement, Optional<ModelElement> nodesElement, String name, String index) {
            StorageGroup group = new StorageGroup(
                    isHosted, name, index,
                    childAsString(groupElement, "distribution.partitions"),
                    booleanAttributeOr(groupElement, VespaDomBuilder.CPU_SOCKET_AFFINITY_ATTRIB_NAME, false),
                    childAsLong(groupElement, VespaDomBuilder.MMAP_NOCORE_LIMIT),
                    childAsBoolean(groupElement, VespaDomBuilder.CORE_ON_OOM),
                    childAsString(groupElement, VespaDomBuilder.NO_VESPAMALLOC),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC_DEBUG),
                    childAsString(groupElement, VespaDomBuilder.VESPAMALLOC_DEBUG_STACKTRACE));

            List<GroupBuilder> subGroups = groupElement.isPresent() ? collectSubGroups(isHosted, group, groupElement.get())
                                                                    : List.of();

            List<XmlNodeBuilder> explicitNodes = new ArrayList<>();
            explicitNodes.addAll(collectExplicitNodes(groupElement));
            explicitNodes.addAll(collectExplicitNodes(nodesElement));

            if (subGroups.size() > 0 && nodesElement.isPresent())
                throw new IllegalArgumentException("A group can contain either explicit subgroups or a nodes specification, but not both.");

            Optional<NodesSpecification> nodeRequirement;
            if (nodesElement.isPresent() && nodesElement.get().stringAttribute("count") != null ) // request these nodes
                nodeRequirement = Optional.of(NodesSpecification.from(nodesElement.get(), context));
            else if (nodesElement.isPresent() && context.getDeployState().isHosted() && context.getDeployState().zone().environment().isManuallyDeployed() ) // default to 1 node
                nodeRequirement = Optional.of(NodesSpecification.from(nodesElement.get(), context));
            else if (nodesElement.isEmpty() && subGroups.isEmpty() && context.getDeployState().isHosted()) // request one node
                nodeRequirement = Optional.of(NodesSpecification.nonDedicated(1, context));
            else if (nodesElement.isPresent() && nodesElement.get().stringAttribute("count") == null && context.getDeployState().isHosted())
                throw new IllegalArgumentException("""
                                                           Clusters in hosted environments must have a <nodes count='N'> tag
                                                           matching all zones, and having no <node> subtags,
                                                           see https://cloud.vespa.ai/en/reference/services""");
            else // Nodes or groups explicitly listed - resolve in GroupBuilder
                nodeRequirement = Optional.empty();

            return new GroupBuilder(group, subGroups, explicitNodes, nodeRequirement);
        }

        private Optional<String> childAsString(Optional<ModelElement> element, String childTagName) {
            if (element.isEmpty()) return Optional.empty();
            return Optional.ofNullable(element.get().childAsString(childTagName));
        }
        private Optional<Long> childAsLong(Optional<ModelElement> element, String childTagName) {
            if (element.isEmpty()) return Optional.empty();
            return Optional.ofNullable(element.get().childAsLong(childTagName));
        }
        private Optional<Boolean> childAsBoolean(Optional<ModelElement> element, String childTagName) {
            if (element.isEmpty()) return Optional.empty();
            return Optional.ofNullable(element.get().childAsBoolean(childTagName));
        }

        private boolean booleanAttributeOr(Optional<ModelElement> element, String attributeName, boolean defaultValue) {
            return element.map(modelElement -> modelElement.booleanAttribute(attributeName, defaultValue)).orElse(defaultValue);
        }

        private Optional<ModelElement> getNodes(ModelElement groupOrNodesElement) {
            if (groupOrNodesElement.getXml().getTagName().equals("nodes")) return Optional.of(groupOrNodesElement);
            return Optional.ofNullable(groupOrNodesElement.child("nodes"));
        }

        private List<XmlNodeBuilder> collectExplicitNodes(Optional<ModelElement> groupOrNodesElement) {
            if (groupOrNodesElement.isEmpty()) return Collections.emptyList();
            List<XmlNodeBuilder> nodes = new ArrayList<>();
            for (ModelElement n : groupOrNodesElement.get().subElements("node"))
                nodes.add(new XmlNodeBuilder(clusterElement, n));
            return nodes;
        }

        private List<GroupBuilder> collectSubGroups(boolean isHosted, StorageGroup parentGroup, ModelElement parentGroupElement) {
            List<ModelElement> subGroupElements = parentGroupElement.subElements("group");
            if (subGroupElements.size() > 1 && parentGroup.getPartitions().isEmpty())
                throw new IllegalArgumentException("'distribution' attribute is required with multiple subgroups");

            List<GroupBuilder> subGroups = new ArrayList<>();
            String indexPrefix = "";
            if (parentGroup.index != null) {
                indexPrefix = parentGroup.index + ".";
            }
            for (ModelElement g : subGroupElements) {
                subGroups.add(collectGroup(isHosted, Optional.of(g), Optional.ofNullable(g.child("nodes")), g.stringAttribute("name"),
                                           indexPrefix + g.integerAttribute("distribution-key")));
            }
            return subGroups;
        }

        private static StorageNode createStorageNode(DeployState deployState, ContentCluster parent, HostResource hostResource, StorageGroup parentGroup, ClusterMembership clusterMembership) {
            StorageNode sNode = new StorageNode(deployState.getProperties(), parent.getStorageCluster(), null, clusterMembership.index(), clusterMembership.retired());
            sNode.setHostResource(hostResource);
            sNode.initService(deployState);

            // TODO: Supplying null as XML is not very nice
            PersistenceEngine provider = parent.getPersistence().create(deployState, sNode, parentGroup, null);
            Distributor d = new Distributor(deployState.getProperties(), parent.getDistributorNodes(), clusterMembership.index(), null, provider);
            d.setHostResource(sNode.getHostResource());
            d.initService(deployState);
            return sNode;
        }
    }

}
