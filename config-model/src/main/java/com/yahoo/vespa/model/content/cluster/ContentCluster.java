// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.Zone;
import com.yahoo.vespa.config.content.MessagetyperouteselectorpolicyConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.content.core.BucketspacesConfig;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.Service;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.monitoring.Metric;
import com.yahoo.vespa.model.admin.monitoring.MetricsConsumer;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerCluster;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerComponent;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerConfigurer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerClusterVerifier;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.xml.ContainerModelBuilder;
import com.yahoo.vespa.model.content.*;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.content.engines.VDSEngine;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.MultilevelDispatchValidator;
import com.yahoo.vespa.model.search.Tuning;
import org.w3c.dom.Element;

import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * A content cluster.
 *
 * @author mostly somebody unknown
 * @author bratseth
 */
public class ContentCluster extends AbstractConfigProducer implements StorDistributionConfig.Producer,
        StorDistributormanagerConfig.Producer,
        FleetcontrollerConfig.Producer,
        MetricsmanagerConfig.Producer,
        MessagetyperouteselectorpolicyConfig.Producer,
        BucketspacesConfig.Producer {

    // TODO: Make private
    private String documentSelection;
    ContentSearchCluster search;
    private final Map<String, NewDocumentType> documentDefinitions;
    private final Set<NewDocumentType> globallyDistributedDocuments;
    // Experimental flag (TODO: remove when feature is enabled by default)
    private boolean enableMultipleBucketSpaces = false;
    com.yahoo.vespa.model.content.StorageGroup rootGroup;
    StorageCluster storageNodes;
    DistributorCluster distributorNodes;
    private Redundancy redundancy;
    ClusterControllerConfig clusterControllerConfig;
    PersistenceEngine.PersistenceFactory persistenceFactory;
    String clusterName;
    Integer maxNodesPerMerge;
    private Zone zone;

    /**
     * If multitenant or a cluster controller was explicitly configured in this cluster:
     * The cluster controller cluster of this particular content cluster.
     *
     * Otherwise: null - the cluster controller is shared by all content clusters and part of Admin.
     */
    private ContainerCluster clusterControllers;

    public enum DistributionMode { LEGACY, STRICT, LOOSE }
    DistributionMode distributionMode;

    public static class Builder {

        /** The admin model of this system or null if none (which only happens in tests) */
        private final Admin admin;

        public Builder(Admin admin) {
            this.admin = admin;
        }
        
        public ContentCluster build(Collection<ContainerModel> containers, ConfigModelContext context, Element w3cContentElement) {

            ModelElement contentElement = new ModelElement(w3cContentElement);

            ModelElement documentsElement = contentElement.getChild("documents");
            Map<String, NewDocumentType> documentDefinitions =
                    new SearchDefinitionBuilder().build(context.getDeployState().getDocumentModel().getDocumentManager(), documentsElement);

            String routingSelection = new DocumentSelectionBuilder().build(documentsElement);
            Redundancy redundancy = new RedundancyBuilder().build(contentElement);
            Set<NewDocumentType> globallyDistributedDocuments = new GlobalDistributionBuilder(documentDefinitions).build(documentsElement);

            ContentCluster c = new ContentCluster(context.getParentProducer(), getClusterName(contentElement), documentDefinitions, 
                                                  globallyDistributedDocuments, routingSelection, redundancy,
                                                  context.getDeployState().getProperties().zone());
            c.clusterControllerConfig = new ClusterControllerConfig.Builder(getClusterName(contentElement), contentElement).build(c, contentElement.getXml());
            c.search = new ContentSearchCluster.Builder(documentDefinitions, globallyDistributedDocuments).build(c, contentElement.getXml());
            c.persistenceFactory = new EngineFactoryBuilder().build(contentElement, c);
            c.storageNodes = new StorageCluster.Builder().build(c, w3cContentElement);
            c.distributorNodes = new DistributorCluster.Builder(c).build(c, w3cContentElement);
            c.rootGroup = new StorageGroup.Builder(contentElement, c, context).buildRootGroup();
            validateThatGroupSiblingsAreUnique(c.clusterName, c.rootGroup);
            redundancy.setExplicitGroups(c.getRootGroup().getNumberOfLeafGroups());
            c.search.handleRedundancy(redundancy);

            IndexedSearchCluster index = c.search.getIndexed();
            if (index != null) {
                setupIndexedCluster(index, contentElement);
            }

            if (c.search.hasIndexedCluster() && !(c.persistenceFactory instanceof ProtonEngine.Factory) ) {
                throw new RuntimeException("If you have indexed search you need to have proton as engine");
            }

            if (c.isMemfilePersistence()) {
                admin.deployLogger().log(Level.WARNING, "'vds' engine is deprecated and will soon be removed. 'proton' is only recommended engine.");
            }

            if (documentsElement != null) {
                ModelElement e = documentsElement.getChild("document-processing");
                if (e != null) {
                    setupDocumentProcessing(c, e);
                }
            } else if (c.persistenceFactory != null) {
                throw new IllegalArgumentException("The specified content engine requires the <documents> element to be specified.");
            }

            ModelElement tuning = contentElement.getChild("tuning");
            if (tuning != null) {
                setupTuning(c, tuning);
            }
            ModelElement experimental = contentElement.getChild("experimental");
            if (experimental != null) {
                setupExperimental(c, experimental);
            }

            if (context.getParentProducer().getRoot() == null) return c;

            addClusterControllers(containers, context, c.rootGroup, contentElement, c.clusterName, c);
            return c;
        }

        private void setupIndexedCluster(IndexedSearchCluster index, ModelElement element) {
            ContentSearch search = DomContentSearchBuilder.build(element);
            Double queryTimeout = search.getQueryTimeout();
            if (queryTimeout != null) {
                Preconditions.checkState(index.getQueryTimeout() == null,
                        "You may not specify query-timeout in both proton and content.");
                index.setQueryTimeout(queryTimeout);
            }
            Double visibilityDelay = search.getVisibilityDelay();
            if (visibilityDelay != null) {
                index.setVisibilityDelay(visibilityDelay);
            }
            index.setSearchCoverage(DomSearchCoverageBuilder.build(element));
            index.setDispatchSpec(DomDispatchBuilder.build(element));
            if (index.useMultilevelDispatchSetup()) {
                // We must validate this before we add tlds and setup the dispatch groups.
                // This must therefore happen before the regular validate() step.
                new MultilevelDispatchValidator(index.getClusterName(), index.getDispatchSpec(), index.getSearchNodes()).validate();
            }

            // TODO: This should be cleaned up to avoid having to change code in 100 places
            // every time we add a dispatch option.
            TuningDispatch tuningDispatch = DomTuningDispatchBuilder.build(element);
            Integer maxHitsPerPartition = tuningDispatch.getMaxHitsPerPartition();
            Boolean useLocalNode = tuningDispatch.getUseLocalNode();

            if (index.getTuning() == null) {
                index.setTuning(new Tuning(index));
            }
            if (index.getTuning().dispatch == null) {
                index.getTuning().dispatch = new Tuning.Dispatch();
            }
            if (maxHitsPerPartition != null) {
                index.getTuning().dispatch.maxHitsPerPartition = maxHitsPerPartition;
            }
            if (useLocalNode != null) {
                index.getTuning().dispatch.useLocalNode = useLocalNode;
            }
            index.getTuning().dispatch.minGroupCoverage = tuningDispatch.getMinGroupCoverage();
            index.getTuning().dispatch.minActiveDocsCoverage = tuningDispatch.getMinActiveDocsCoverage();
            index.getTuning().dispatch.policy = tuningDispatch.getDispatchPolicy();
        }

        private void setupDocumentProcessing(ContentCluster c, ModelElement e) {
            String docprocCluster = e.getStringAttribute("cluster");
            if (docprocCluster != null) {
                docprocCluster = docprocCluster.trim();
            }
            if (c.getSearch().hasIndexedCluster()) {
                if (docprocCluster != null && !docprocCluster.isEmpty()) {
                    c.getSearch().getIndexed().setIndexingClusterName(docprocCluster);
                }
            }

            String docprocChain = e.getStringAttribute("chain");
            if (docprocChain != null) {
                docprocChain = docprocChain.trim();
            }
            if (c.getSearch().hasIndexedCluster()) {
                if (docprocChain != null && !docprocChain.isEmpty()) {
                    c.getSearch().getIndexed().setIndexingChainName(docprocChain);
                }
            }
        }

        private void setupTuning(ContentCluster c, ModelElement tuning) {
            ModelElement distribution = tuning.getChild("distribution");
            if (distribution != null) {
                String attr = distribution.getStringAttribute("type");
                if (attr != null) {
                    if (attr.toLowerCase().equals("strict")) {
                        c.distributionMode = DistributionMode.STRICT;
                    } else if (attr.toLowerCase().equals("loose")) {
                        c.distributionMode = DistributionMode.LOOSE;
                    } else if (attr.toLowerCase().equals("legacy")) {
                        c.distributionMode = DistributionMode.LEGACY;
                    } else {
                        throw new IllegalStateException("Distribution type " + attr + " not supported.");
                    }
                }
            }
            ModelElement merges = tuning.getChild("merges");
            if (merges != null) {
                Integer attr = merges.getIntegerAttribute("max-nodes-per-merge");
                if (attr != null) {
                    c.maxNodesPerMerge = attr;
                }
            }
        }

        private void setupExperimental(ContentCluster cluster, ModelElement experimental) {
            Boolean enableMultipleBucketSpaces = experimental.childAsBoolean("enable-multiple-bucket-spaces");
            if (enableMultipleBucketSpaces != null) {
                cluster.enableMultipleBucketSpaces = enableMultipleBucketSpaces;
            }
        }

        private void validateGroupSiblings(String cluster, StorageGroup group) {
            HashSet<String> siblings = new HashSet<>();
            for (StorageGroup g : group.getSubgroups()) {
                String name = g.getName();
                if (siblings.contains(name)) {
                    throw new IllegalArgumentException("Cluster '" + cluster + "' has multiple groups " +
                            "with name '" + name + "' in the same subgroup. Group sibling names must be unique.");
                }
                siblings.add(name);
            }
        }

        private void validateThatGroupSiblingsAreUnique(String cluster, StorageGroup group) {
            if (group == null) {
                return; // Unit testing case
            }
            validateGroupSiblings(cluster, group);
            for (StorageGroup g : group.getSubgroups()) {
                validateThatGroupSiblingsAreUnique(cluster, g);
            }
        }

        private void addClusterControllers(Collection<ContainerModel> containers, ConfigModelContext context, 
                                           StorageGroup rootGroup, ModelElement contentElement, 
                                           String contentClusterName, ContentCluster contentCluster) {
            if (admin == null) return; // only in tests
            if (contentCluster.getPersistence() == null) return;

            ContainerCluster clusterControllers;

            ContentCluster overlappingCluster = findOverlappingCluster(context.getParentProducer().getRoot(), contentCluster);
            if (overlappingCluster != null && overlappingCluster.getClusterControllers() != null) {
                // Borrow the cluster controllers of the other cluster in this case.
                // This condition only obtains on non-hosted systems with a shared config server,
                // a combination which only exists in system tests
                clusterControllers = overlappingCluster.getClusterControllers();
            }
            else if (admin.multitenant()) {
                String clusterName = contentClusterName + "-controllers";
                NodesSpecification nodesSpecification =
                    NodesSpecification.optionalDedicatedFromParent(contentElement.getChild("controllers"), context.getDeployState().getWantedNodeVespaVersion())
                                      .orElse(NodesSpecification.nonDedicated(3, context.getDeployState().getWantedNodeVespaVersion()));
                Collection<HostResource> hosts = nodesSpecification.isDedicated() ?
                                                 getControllerHosts(nodesSpecification, admin, clusterName, context) :
                                                 drawControllerHosts(nodesSpecification.count(), rootGroup, containers);

                clusterControllers = createClusterControllers(new ClusterControllerCluster(contentCluster, "standalone"), hosts, clusterName, true);
                contentCluster.clusterControllers = clusterControllers;
            }
            else {
                clusterControllers = admin.getClusterControllers();
                if (clusterControllers == null) {
                    List<HostResource> hosts = admin.getClusterControllerHosts();
                    if (hosts.size() > 1) {
                        admin.deployLogger().log(Level.INFO, "When having content cluster(s) and more than 1 config server it is recommended to configure cluster controllers explicitly.");
                    }
                    clusterControllers = createClusterControllers(admin, hosts, "cluster-controllers", false);
                    admin.setClusterControllers(clusterControllers);
                }
            }

            addClusterControllerComponentsForThisCluster(clusterControllers, contentCluster);
        }

        /** Returns any other content cluster which shares nodes with this, or null if none are built */
        private ContentCluster findOverlappingCluster(AbstractConfigProducerRoot root, ContentCluster contentCluster) {
            for (ContentCluster otherContentCluster : root.getChildrenByTypeRecursive(ContentCluster.class)) {
                if (otherContentCluster != contentCluster && overlaps(contentCluster, otherContentCluster))
                    return otherContentCluster;
            }
            return null;
        }

        private boolean overlaps(ContentCluster c1, ContentCluster c2) {
            Set<HostResource> c1Hosts = c1.getRootGroup().recursiveGetNodes().stream().map(StorageNode::getHostResource).collect(Collectors.toSet());
            Set<HostResource> c2Hosts = c2.getRootGroup().recursiveGetNodes().stream().map(StorageNode::getHostResource).collect(Collectors.toSet());
            return ! Sets.intersection(c1Hosts, c2Hosts).isEmpty();
        }

        private Collection<HostResource> getControllerHosts(NodesSpecification nodesSpecification, Admin admin, String clusterName, ConfigModelContext context) {
            return nodesSpecification.provision(admin.getHostSystem(), ClusterSpec.Type.admin, ClusterSpec.Id.from(clusterName), context.getDeployLogger()).keySet();
        }

        private List<HostResource> drawControllerHosts(int count, StorageGroup rootGroup, Collection<ContainerModel> containers) {
            List<HostResource> hostsByName = drawContentHostsRecursively(count, false, rootGroup);
            List<HostResource> hostsByIndex = drawContentHostsRecursively(count, true, rootGroup);
            // if (hosts.size() < count) // supply with containers TODO: Currently disabled due to leading to topology change problems
            //     hosts.addAll(drawContainerHosts(count - hosts.size(), containers, new HashSet<>(hosts)));
            List<HostResource> hosts = HostResource.pickHosts(hostsByName, hostsByIndex, count, 1);
            if (hosts.size() % 2 == 0) // ZK clusters of even sizes are less available (even in the size=2 case)
                hosts = hosts.subList(0, hosts.size()-1);
            return hosts;
        }

        /**
         * Draws <code>count</code> container nodes to use as cluster controllers, or as many as possible
         * if less than <code>count</code> are available.
         * 
         * This will draw the same nodes each time it is 
         * invoked if cluster names and node indexes are unchanged.
         */
        // DO NOT DELETE - see above
        private List<HostResource> drawContainerHosts(int count, Collection<ContainerModel> containerClusters, 
                                                      Set<HostResource> usedHosts) {
            if (containerClusters.isEmpty()) return Collections.emptyList();

            List<HostResource> allHosts = new ArrayList<>();
            for (ContainerCluster cluster : clustersSortedByName(containerClusters))
                allHosts.addAll(hostResourcesSortedByIndex(cluster));
            
            // Don't use hosts already selected to be assigned a cluster controllers as part of building this,
            // and don't use hosts which already have one. One physical host may have many roles but can only
            // have one cluster controller
            List<HostResource> uniqueHostsWithoutClusterController = allHosts.stream()
                    .filter(h -> ! usedHosts.contains(h))
                    .filter(h -> ! hostHasClusterController(h.getHostname(), allHosts))
                    .distinct()
                    .collect(Collectors.toList());

            return uniqueHostsWithoutClusterController.subList(0, Math.min(uniqueHostsWithoutClusterController.size(), count));
        }
        
        private List<ContainerCluster> clustersSortedByName(Collection<ContainerModel> containerModels) {
            return containerModels.stream()
                    .map(ContainerModel::getCluster)
                    .sorted(Comparator.comparing(ContainerCluster::getName))
                    .collect(Collectors.toList());
        }

        private List<HostResource> hostResourcesSortedByIndex(ContainerCluster cluster) {
            return cluster.getContainers().stream()
                    .sorted(Comparator.comparing(Container::index))
                    .map(Container::getHostResource)
                    .collect(Collectors.toList());
        }

        /** Returns whether any host having the given hostname has a cluster controller */
        private boolean hostHasClusterController(String hostname, List<HostResource> hosts) {
            for (HostResource host : hosts) {
                if ( ! host.getHostname().equals(hostname)) continue;

                if (hasClusterController(host))
                    return true;
            }
            return false;
        }

        private boolean hasClusterController(HostResource host) {
            for (Service service : host.getServices())
                if (service instanceof ClusterControllerContainer)
                    return true;
            return false;
        }

        /**
         * Draw <code>count</code> nodes from as many different content groups below this as possible.
         * This will only achieve maximum spread in the case where the groups are balanced and never on the same
         * physical node. It will not achieve maximum spread over all levels in a multilevel group hierarchy.
         */
        // Note: This method cannot be changed to draw different nodes without ensuring that it will draw nodes
        //       which overlaps with previously drawn nodes as this will prevent rolling upgrade
        private List<HostResource> drawContentHostsRecursively(int count, boolean byIndex, StorageGroup group) {
            Set<HostResource> hosts = new HashSet<>();
            if (group.getNodes().isEmpty()) {
                int hostsPerSubgroup = (int)Math.ceil((double)count / group.getSubgroups().size());
                for (StorageGroup subgroup : group.getSubgroups())
                    hosts.addAll(drawContentHostsRecursively(hostsPerSubgroup, byIndex, subgroup));
            }
            else {
                hosts.addAll(group.getNodes().stream()
                     .filter(node -> ! node.isRetired()) // Avoid retired controllers to avoid surprises on expiry
                     .map(StorageNode::getHostResource).collect(Collectors.toList()));
            }

            List<HostResource> sortedHosts = new ArrayList<>(hosts);
            if (byIndex)
                sortedHosts.sort((a, b) -> (a.comparePrimarilyByIndexTo(b)));
            else // by name
                Collections.sort(sortedHosts);
            sortedHosts = sortedHosts.subList(0, Math.min(count, hosts.size()));
            return sortedHosts;
        }

        private ContainerCluster createClusterControllers(AbstractConfigProducer parent, Collection<HostResource> hosts, String name, boolean multitenant) {
            ContainerCluster clusterControllers = new ContainerCluster(parent, name, name, new ClusterControllerClusterVerifier());
            List<Container> containers = new ArrayList<>();
            // Add a cluster controller on each config server (there is always at least one).
            if (clusterControllers.getContainers().isEmpty()) {
                int index = 0;
                for (HostResource host : hosts) {
                    ClusterControllerContainer clusterControllerContainer = new ClusterControllerContainer(clusterControllers, index, multitenant);
                    clusterControllerContainer.setHostResource(host);
                    clusterControllerContainer.initService();
                    clusterControllerContainer.setProp("clustertype", "admin")
                            .setProp("clustername", clusterControllers.getName())
                            .setProp("index", String.valueOf(index));
                    containers.add(clusterControllerContainer);
                    ++index;
                }
            }
            clusterControllers.addContainers(containers);
            ContainerModelBuilder.addDefaultHandler_legacyBuilder(clusterControllers);
            return clusterControllers;
        }

        private void addClusterControllerComponentsForThisCluster(ContainerCluster clusterControllers, ContentCluster contentCluster) {
            int index = 0;
            for (Container container : clusterControllers.getContainers()) {
                if ( ! hasClusterControllerComponent(container))
                    container.addComponent(new ClusterControllerComponent());
                container.addComponent(new ClusterControllerConfigurer(contentCluster, index++, clusterControllers.getContainers().size()));
            }

        }

        private boolean hasClusterControllerComponent(Container container) {
            for (Object o : container.getComponents().getComponents())
                if (o instanceof ClusterControllerComponent) return true;
            return false;
        }

    }

    private ContentCluster(AbstractConfigProducer parent,
                           String clusterName,
                           Map<String, NewDocumentType> documentDefinitions,
                           Set<NewDocumentType> globallyDistributedDocuments,
                           String routingSelection,
                           Redundancy redundancy,
                           Zone zone) {
        super(parent, clusterName);
        this.clusterName = clusterName;
        this.documentDefinitions = documentDefinitions;
        this.globallyDistributedDocuments = globallyDistributedDocuments;
        this.documentSelection = routingSelection;
        this.redundancy = redundancy;
        this.zone = zone;
    }

    public void prepare() {
        search.prepare();

        if (clusterControllers != null) {
            clusterControllers.prepare();
        }
    }

    /** Returns cluster controllers if this is multitenant, null otherwise */
    public ContainerCluster getClusterControllers() { return clusterControllers; }

    public DistributionMode getDistributionMode() {
        if (distributionMode != null) return distributionMode;
        return getPersistence().getDefaultDistributionMode();
    }

    public boolean isMemfilePersistence() {
        return persistenceFactory instanceof VDSEngine.Factory;
    }

    public static String getClusterName(ModelElement clusterElem) {
        String clusterName = clusterElem.getStringAttribute("id");
        if (clusterName == null) {
            clusterName = "content";
        }

        return clusterName;
    }

    public String getName() { return clusterName; }

    public String getRoutingSelector() { return documentSelection; }

    public DistributorCluster getDistributorNodes() { return distributorNodes; }

    public StorageCluster getStorageNodes() { return storageNodes; }

    public ClusterControllerConfig getClusterControllerConfig() { return clusterControllerConfig; }

    public PersistenceEngine.PersistenceFactory getPersistence() { return persistenceFactory; }

    /**
     * The list of documentdefinitions declared at the cluster level.
     * @return the set of documenttype names
     */
    public Map<String, NewDocumentType> getDocumentDefinitions() { return documentDefinitions; }

    public boolean isGloballyDistributed(NewDocumentType docType) {
        return globallyDistributedDocuments.contains(docType);
    }

    public final ContentSearchCluster getSearch() { return search; }

    public Redundancy redundancy() { return redundancy; }

    @Override
    public void getConfig(MessagetyperouteselectorpolicyConfig.Builder builder) {
    	if (!getSearch().hasIndexedCluster()) return;
    	builder.
    	defaultroute(com.yahoo.vespa.model.routing.DocumentProtocol.getDirectRouteName(getConfigId())).
    	route(new MessagetyperouteselectorpolicyConfig.Route.Builder().
    	        messagetype(DocumentProtocol.MESSAGE_PUTDOCUMENT).
    	        name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId()))).
    	route(new MessagetyperouteselectorpolicyConfig.Route.Builder().
    	        messagetype(DocumentProtocol.MESSAGE_REMOVEDOCUMENT).
    	        name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId()))).
    	route(new MessagetyperouteselectorpolicyConfig.Route.Builder().
    	        messagetype(DocumentProtocol.MESSAGE_UPDATEDOCUMENT).
    	        name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId())));
    }
    
    public com.yahoo.vespa.model.content.StorageGroup getRootGroup() {
        return rootGroup;
    }

    @Override
    public void getConfig(StorDistributionConfig.Builder builder) {
        if (rootGroup != null) {
            builder.group.addAll(rootGroup.getGroupStructureConfig());
        }

        if (redundancy != null) {
            redundancy.getConfig(builder);
        }

        if (search.usesHierarchicDistribution()) {
            builder.active_per_leaf_group(true);
        }
    }

    int getNodeCount() {
        return storageNodes.getChildren().size();
    }

    int getNodeCountPerGroup() {
        return rootGroup != null ? getNodeCount() / rootGroup.getNumberOfLeafGroups() : getNodeCount();
    }

    @Override
    public void getConfig(FleetcontrollerConfig.Builder builder) {
        builder.ideal_distribution_bits(distributionBits());
        if (getNodeCount() < 5) {
            builder.min_storage_up_count(1);
            builder.min_distributor_up_ratio(0);
            builder.min_storage_up_ratio(0);
        }
    }

    @Override
    public void getConfig(StorDistributormanagerConfig.Builder builder) {
        builder.minsplitcount(distributionBits());
        if (maxNodesPerMerge != null) {
            builder.maximum_nodes_per_merge(maxNodesPerMerge);
        }
    }

    /**
     * Returns the distribution bits this cluster should use.
     * On Hosted Vespa this is hardcoded and not computed from the nodes because reducing the number of nodes is a common
     * operation, while reducing the number of distribution bits can lead to consistency problems.
     * This hardcoded value should work fine from 1-200 nodes. Those who have more will need to set this value
     * in config and not remove it again if they reduce the node count.
     */
    public int distributionBits() {
        if (zone.environment() == Environment.prod && ! zone.equals(Zone.defaultZone())) {
            return 16;
        }
        else { // hosted test zone, or self-hosted system
            // hosted test zones: have few nodes and use visiting in tests: This is slow with 16 bits (to many buckets)
            // self hosted systems: should probably default to 16 bits, but the transition may cause problems
            return DistributionBitCalculator.getDistributionBits(getNodeCountPerGroup(), getDistributionMode());
        }
    }

    @Override
    public void validate() throws Exception {
        super.validate();
        if (search.usesHierarchicDistribution() && ! isHostedVespa()) {
            // validate manually configured groups
            new IndexedHierarchicDistributionValidator(search.getClusterName(), rootGroup, redundancy, search.getIndexed().getTuning().dispatch.policy).validate();
            if (search.getIndexed().useMultilevelDispatchSetup()) {
                throw new IllegalArgumentException("In indexed content cluster '" + search.getClusterName() + "': Using multi-level dispatch setup is not supported when using hierarchical distribution.");
            }
        }
        new ReservedDocumentTypeNameValidator().validate(documentDefinitions);
        new GlobalDistributionValidator().validate(documentDefinitions, globallyDistributedDocuments, redundancy, enableMultipleBucketSpaces);
    }

    public static Map<String, Integer> METRIC_INDEX_MAP = new TreeMap<>();
    static {
        METRIC_INDEX_MAP.put("status", 0);
        METRIC_INDEX_MAP.put("log", 1);
        METRIC_INDEX_MAP.put("yamas", 2);
        METRIC_INDEX_MAP.put("health", 3);
        METRIC_INDEX_MAP.put("fleetcontroller", 4);
        METRIC_INDEX_MAP.put("statereporter", 5);
    }

    public static MetricsmanagerConfig.Consumer.Builder getMetricBuilder(String name, MetricsmanagerConfig.Builder builder) {
        Integer index = METRIC_INDEX_MAP.get(name);
        if (index != null) {
            return builder.consumer.get(index);
        }

        MetricsmanagerConfig.Consumer.Builder retVal = new MetricsmanagerConfig.Consumer.Builder();
        retVal.name(name);
        builder.consumer(retVal);
        return retVal;
    }

    @Override
    public void getConfig(MetricsmanagerConfig.Builder builder) {
        Monitoring monitoring = getMonitoringService();
        if (monitoring != null) {
            builder.snapshot(new MetricsmanagerConfig.Snapshot.Builder().
                    periods(monitoring.getIntervalSeconds()).periods(300));
        }
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("status").
                        addedmetrics("*").
                        removedtags("partofsum"));

        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("log").
                        tags("logdefault").
                        removedtags("loadtype"));
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("yamas").
                        tags("yamasdefault").
                        removedtags("loadtype"));
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("health"));
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("fleetcontroller"));
        builder.consumer(
                new MetricsmanagerConfig.Consumer.Builder().
                        name("statereporter").
                        addedmetrics("*").
                        removedtags("thread").
                        tags("disk"));

        Map<String, MetricsConsumer> consumers = getRoot().getAdmin().getLegacyUserMetricsConsumers();
        if (consumers != null) {
           for (Map.Entry<String, MetricsConsumer> e : consumers.entrySet()) {
                MetricsmanagerConfig.Consumer.Builder b = getMetricBuilder(e.getKey(), builder);
                for (Metric m : e.getValue().getMetrics().values()) {
                    b.addedmetrics(m.name);
                }
            }
        }

    }

    private static final String DEFAULT_BUCKET_SPACE = "default";
    private static final String GLOBAL_BUCKET_SPACE = "global";

    @Override
    public void getConfig(BucketspacesConfig.Builder builder) {
        for (NewDocumentType docType : getDocumentDefinitions().values()) {
            BucketspacesConfig.Documenttype.Builder docTypeBuilder = new BucketspacesConfig.Documenttype.Builder();
            docTypeBuilder.name(docType.getName());
            String bucketSpace = ((enableMultipleBucketSpaces && isGloballyDistributed(docType))
                    ? GLOBAL_BUCKET_SPACE : DEFAULT_BUCKET_SPACE);
            docTypeBuilder.bucketspace(bucketSpace);
            builder.documenttype(docTypeBuilder);
        }
        builder.enable_multiple_bucket_spaces(enableMultipleBucketSpaces);
    }
}
