// Copyright Verizon Media. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content.cluster;

import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.yahoo.config.application.api.DeployLogger;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.AbstractConfigProducerRoot;
import com.yahoo.config.provision.ClusterMembership;
import com.yahoo.config.provision.ClusterSpec;
import com.yahoo.config.provision.Environment;
import com.yahoo.config.provision.NodeResources;
import com.yahoo.config.provision.Zone;
import com.yahoo.documentapi.messagebus.protocol.DocumentProtocol;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.metrics.MetricsmanagerConfig;
import com.yahoo.vespa.config.content.AllClustersBucketSpacesConfig;
import com.yahoo.vespa.config.content.DistributionConfig;
import com.yahoo.vespa.config.content.FleetcontrollerConfig;
import com.yahoo.vespa.config.content.MessagetyperouteselectorpolicyConfig;
import com.yahoo.vespa.config.content.StorDistributionConfig;
import com.yahoo.vespa.config.content.core.BucketspacesConfig;
import com.yahoo.vespa.config.content.core.StorDistributormanagerConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerCluster;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerComponent;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerConfigurer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainer;
import com.yahoo.vespa.model.admin.clustercontroller.ClusterControllerContainerCluster;
import com.yahoo.vespa.model.admin.clustercontroller.ReindexingContext;
import com.yahoo.vespa.model.admin.monitoring.Monitoring;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.NodesSpecification;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.content.ClusterControllerConfig;
import com.yahoo.vespa.model.content.ClusterResourceLimits;
import com.yahoo.vespa.model.content.ContentSearch;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.DistributionBitCalculator;
import com.yahoo.vespa.model.content.DistributorCluster;
import com.yahoo.vespa.model.content.GlobalDistributionValidator;
import com.yahoo.vespa.model.content.IndexedHierarchicDistributionValidator;
import com.yahoo.vespa.model.content.Redundancy;
import com.yahoo.vespa.model.content.ReservedDocumentTypeNameValidator;
import com.yahoo.vespa.model.content.StorageGroup;
import com.yahoo.vespa.model.content.StorageNode;
import com.yahoo.vespa.model.content.engines.PersistenceEngine;
import com.yahoo.vespa.model.content.engines.ProtonEngine;
import com.yahoo.vespa.model.content.storagecluster.StorageCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.Tuning;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * A content cluster.
 *
 * @author mostly somebody unknown
 * @author bratseth
 */
public class ContentCluster extends AbstractConfigProducer implements
                                                           DistributionConfig.Producer,
                                                           StorDistributionConfig.Producer,
                                                           StorDistributormanagerConfig.Producer,
                                                           FleetcontrollerConfig.Producer,
                                                           MetricsmanagerConfig.Producer,
                                                           MessagetyperouteselectorpolicyConfig.Producer,
                                                           BucketspacesConfig.Producer {

    private final String documentSelection;
    private ContentSearchCluster search;
    private final boolean isHosted;
    private final Map<String, NewDocumentType> documentDefinitions;
    private final Set<NewDocumentType> globallyDistributedDocuments;
    private com.yahoo.vespa.model.content.StorageGroup rootGroup;
    private StorageCluster storageNodes;
    private DistributorCluster distributorNodes;
    private Redundancy redundancy;
    private ClusterControllerConfig clusterControllerConfig;
    private PersistenceEngine.PersistenceFactory persistenceFactory;
    private final String clusterId;
    private Integer maxNodesPerMerge;
    private final Zone zone;

    /**
     * If multitenant or a cluster controller was explicitly configured in this cluster:
     * The cluster controller cluster of this particular content cluster.
     *
     * Otherwise: null - the cluster controller is shared by all content clusters and part of Admin.
     */
    private ClusterControllerContainerCluster clusterControllers;

    public enum DistributionMode { LEGACY, STRICT, LOOSE }
    private DistributionMode distributionMode;

    public static class Builder {

        /** The admin model of this system or null if none (which only happens in tests) */
        private final Admin admin;

        public Builder(Admin admin) {
            this.admin = admin;
        }
        
        public ContentCluster build(Collection<ContainerModel> containers, ConfigModelContext context, Element w3cContentElement) {
            ModelElement contentElement = new ModelElement(w3cContentElement);
            DeployState deployState = context.getDeployState();
            ModelElement documentsElement = contentElement.child("documents");
            Map<String, NewDocumentType> documentDefinitions =
                    new SearchDefinitionBuilder().build(deployState.getDocumentModel().getDocumentManager(), documentsElement);

            String routingSelection = new DocumentSelectionBuilder().build(documentsElement);
            RedundancyBuilder redundancyBuilder = new RedundancyBuilder(contentElement);
            Set<NewDocumentType> globallyDistributedDocuments = new GlobalDistributionBuilder(documentDefinitions).build(documentsElement);

            ContentCluster c = new ContentCluster(context.getParentProducer(), getClusterId(contentElement), documentDefinitions,
                                                  globallyDistributedDocuments, routingSelection,
                                                  deployState.zone(), deployState.isHosted());
            boolean enableFeedBlockInDistributor = deployState.getProperties().featureFlags().enableFeedBlockInDistributor();
            var resourceLimits = new ClusterResourceLimits.Builder(enableFeedBlockInDistributor).build(contentElement);
            c.clusterControllerConfig = new ClusterControllerConfig.Builder(getClusterId(contentElement),
                    contentElement,
                    resourceLimits.getClusterControllerLimits()).build(deployState, c, contentElement.getXml());
            c.search = new ContentSearchCluster.Builder(documentDefinitions,
                    globallyDistributedDocuments,
                    isCombined(getClusterId(contentElement), containers),
                    resourceLimits.getContentNodeLimits()).build(deployState, c, contentElement.getXml());
            c.persistenceFactory = new EngineFactoryBuilder().build(contentElement, c);
            c.storageNodes = new StorageCluster.Builder().build(deployState, c, w3cContentElement);
            c.distributorNodes = new DistributorCluster.Builder(c).build(deployState, c, w3cContentElement);
            c.rootGroup = new StorageGroup.Builder(contentElement, context).buildRootGroup(deployState, redundancyBuilder, c);
            validateThatGroupSiblingsAreUnique(c.clusterId, c.rootGroup);
            c.search.handleRedundancy(c.redundancy);
            setupSearchCluster(c.search, contentElement, deployState.getDeployLogger());

            if (c.search.hasIndexedCluster() && !(c.persistenceFactory instanceof ProtonEngine.Factory) )
                throw new RuntimeException("Indexed search requires proton as engine");

            if (documentsElement != null) {
                ModelElement e = documentsElement.child("document-processing");
                if (e != null)
                    setupDocumentProcessing(c, e);
            } else if (c.persistenceFactory != null) {
                throw new IllegalArgumentException("The specified content engine requires the <documents> element to be specified.");
            }

            ModelElement tuning = contentElement.child("tuning");
            if (tuning != null)
                setupTuning(c, tuning);

            ModelElement experimental = contentElement.child("experimental");
            if (experimental != null)
                setupExperimental(c, experimental);

            if (context.getParentProducer().getRoot() == null) return c;

            addClusterControllers(context, c.rootGroup, contentElement, c.clusterId, c, deployState);
            return c;
        }

        private void setupSearchCluster(ContentSearchCluster csc, ModelElement element, DeployLogger logger) {
            ContentSearch search = DomContentSearchBuilder.build(element);
            Double visibilityDelay = search.getVisibilityDelay();
            if (visibilityDelay != null) {
                csc.setVisibilityDelay(visibilityDelay);
            }
            if (csc.hasIndexedCluster()) {
                setupIndexedCluster(csc.getIndexed(), search, element, logger);
            }


        }
        private void setupIndexedCluster(IndexedSearchCluster index, ContentSearch search, ModelElement element, DeployLogger logger) {
            Double queryTimeout = search.getQueryTimeout();
            if (queryTimeout != null) {
                Preconditions.checkState(index.getQueryTimeout() == null,
                        "In " + index + ": You may not specify query-timeout in both proton and content.");
                index.setQueryTimeout(queryTimeout);
            }
            index.setSearchCoverage(DomSearchCoverageBuilder.build(element));
            index.setDispatchSpec(DomDispatchBuilder.build(element));

            if (index.getTuning() == null)
                index.setTuning(new Tuning(index));
            index.getTuning().dispatch = DomTuningDispatchBuilder.build(element, logger);
        }

        private void setupDocumentProcessing(ContentCluster c, ModelElement e) {
            String docprocCluster = e.stringAttribute("cluster");
            if (docprocCluster != null) {
                docprocCluster = docprocCluster.trim();
            }
            if (c.getSearch().hasIndexedCluster()) {
                if (docprocCluster != null && !docprocCluster.isEmpty()) {
                    c.getSearch().getIndexed().setIndexingClusterName(docprocCluster);
                }
            }

            String docprocChain = e.stringAttribute("chain");
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
            ModelElement distribution = tuning.child("distribution");
            if (distribution != null) {
                String attr = distribution.stringAttribute("type");
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
            ModelElement merges = tuning.child("merges");
            if (merges != null) {
                Integer attr = merges.integerAttribute("max-nodes-per-merge");
                if (attr != null) {
                    c.maxNodesPerMerge = attr;
                }
            }
        }

        /** Returns whether this hosts one of the given container clusters */
        private boolean isCombined(String clusterId, Collection<ContainerModel> containers) {
            return containers.stream()
                             .map(model -> model.getCluster().getHostClusterId())
                             .filter(Optional::isPresent)
                             .anyMatch(id -> id.get().equals(clusterId));
        }

        private void setupExperimental(ContentCluster cluster, ModelElement experimental) {
            // Put handling of experimental flags here
        }

        private void validateGroupSiblings(String cluster, StorageGroup group) {
            Set<String> siblings = new HashSet<>();
            for (StorageGroup g : group.getSubgroups()) {
                String name = g.getName();
                if (siblings.contains(name)) {
                    throw new IllegalArgumentException("Cluster '" + cluster + "' has multiple groups " +
                                                       "with name '" + name +
                                                       "' in the same subgroup. Group sibling names must be unique.");
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

        private void addClusterControllers(ConfigModelContext context,
                                           StorageGroup rootGroup,
                                           ModelElement contentElement,
                                           String contentClusterName,
                                           ContentCluster contentCluster,
                                           DeployState deployState) {
            if (admin == null) return; // only in tests
            if (contentCluster.getPersistence() == null) return;

            ClusterControllerContainerCluster clusterControllers;

            ContentCluster overlappingCluster = findOverlappingCluster(context.getParentProducer().getRoot(), contentCluster);

            if (overlappingCluster != null && overlappingCluster.getClusterControllers() != null) {
                // Borrow the cluster controllers of the other cluster in this case.
                // This condition only occurs on non-hosted systems with a shared config server,
                // a combination which only exists in system tests.
                clusterControllers = overlappingCluster.getClusterControllers();
            }
            else if (admin.multitenant()) {
                if (context.properties().dedicatedClusterControllerCluster())
                    clusterControllers = getDedicatedSharedControllers(contentElement, admin, context, deployState);
                else {
                    clusterControllers = createClusterControllers(new ClusterControllerCluster(contentCluster, "standalone", deployState),
                                                                  drawControllerHosts(3, rootGroup),
                                                                  contentClusterName + "-controllers",
                                                                  true,
                                                                  context.getDeployState());
                    contentCluster.clusterControllers = clusterControllers;
                }
            }
            else {
                clusterControllers = admin.getClusterControllers();
                if (clusterControllers == null) {
                    List<HostResource> hosts = admin.getClusterControllerHosts();
                    if (hosts.size() > 1) {
                        context.getDeployState().getDeployLogger().log(Level.INFO,
                                                                       "When having content cluster(s) and more than 1 config server it is recommended to configure cluster controllers explicitly.");
                    }
                    clusterControllers = createClusterControllers(admin, hosts, "cluster-controllers", false, context.getDeployState());
                    admin.setClusterControllers(clusterControllers);
                }
            }

            addClusterControllerComponentsForThisCluster(clusterControllers, contentCluster);
            ReindexingContext reindexingContext = clusterControllers.reindexingContext();
            for (NewDocumentType type : contentCluster.documentDefinitions.values()) {
                reindexingContext.addDocumentType(contentCluster.clusterId, type);
            }
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

        public static final NodeResources clusterControllerResources = new NodeResources(0.5, 2, 10, 0.3, NodeResources.DiskSpeed.any, NodeResources.StorageType.any);

        private ClusterControllerContainerCluster getDedicatedSharedControllers(ModelElement contentElement, Admin admin,
                                                                                ConfigModelContext context, DeployState deployState) {
            if (admin.getClusterControllers() == null) {
                NodesSpecification spec = NodesSpecification.requiredFromSharedParents(deployState.zone().environment().isProduction() ? 3 : 1,
                                                                                       deployState.featureFlags().dedicatedClusterControllerFlavor().orElse(clusterControllerResources),
                                                                                       contentElement,
                                                                                       context);

                Collection<HostResource> hosts = spec.provision(admin.hostSystem(),
                                                                ClusterSpec.Type.admin,
                                                                ClusterSpec.Id.from("cluster-controllers"),
                                                                context.getDeployLogger(),
                                                                true)
                                                     .keySet();

                admin.setClusterControllers(createClusterControllers(new ClusterControllerCluster(admin, "standalone", deployState),
                                                                     hosts,
                                                                     "cluster-controllers",
                                                                     true,
                                                                     context.getDeployState()));
            }
            return admin.getClusterControllers();
        }

        private List<HostResource> drawControllerHosts(int count, StorageGroup rootGroup) {
            List<HostResource> hosts = drawControllerHosts(count, false, rootGroup);
            List<HostResource> retiredHosts = drawControllerHosts(count, true, rootGroup);

            // preserve the cluster state in case all pre-existing controllers are on retired nodes
            List<HostResource> all = new ArrayList<>(hosts);
            all.addAll(retiredHosts);
            return all;
        }

        private List<HostResource> drawControllerHosts(int count, boolean retired, StorageGroup rootGroup) {
            List<HostResource> hosts = drawContentHostsRecursively(count, retired, rootGroup);
            if (hosts.size() % 2 == 0 && ! hosts.isEmpty()) // ZK clusters of even sizes are less available (even in the size=2 case)
                hosts = hosts.subList(0, hosts.size()-1);
            return hosts;
        }

        /**
         * Draw <code>count</code> nodes from as many different content groups below this as possible.
         * This will only achieve maximum spread in the case where the groups are balanced and never on the same
         * physical node. It will not achieve maximum spread over all levels in a multilevel group hierarchy.
         */
        // Note: This method cannot be changed to draw different nodes without ensuring that it will draw nodes
        //       which overlaps with previously drawn nodes as that will prevent rolling upgrade
        private List<HostResource> drawContentHostsRecursively(int count, boolean retired, StorageGroup group) {
            Set<HostResource> hosts = new HashSet<>();
            if (group.getNodes().isEmpty()) {
                int hostsPerSubgroup = (int)Math.ceil((double)count / group.getSubgroups().size());
                for (StorageGroup subgroup : group.getSubgroups())
                    hosts.addAll(drawContentHostsRecursively(hostsPerSubgroup, retired, subgroup));
            }
            else {
                hosts.addAll(group.getNodes().stream()
                     .filter(node -> node.isRetired() == retired)
                     .map(StorageNode::getHostResource).collect(toList()));
            }

            List<HostResource> sortedHosts = new ArrayList<>(hosts);
            sortedHosts.sort(HostResource::comparePrimarilyByIndexTo);
            sortedHosts = sortedHosts.subList(0, Math.min(count, hosts.size()));
            return sortedHosts;
        }

        private ClusterControllerContainerCluster createClusterControllers(AbstractConfigProducer<?> parent,
                                                                           Collection<HostResource> hosts,
                                                                           String name,
                                                                           boolean multitenant,
                                                                           DeployState deployState) {
            var clusterControllers = new ClusterControllerContainerCluster(parent, name, name, deployState);
            List<ClusterControllerContainer> containers = new ArrayList<>();
            // Add a cluster controller on each config server (there is always at least one).
            if (clusterControllers.getContainers().isEmpty()) {
                int index = 0;
                for (HostResource host : hosts) {
                    int ccIndex = deployState.getProperties().dedicatedClusterControllerCluster()
                                  ? host.spec().membership().map(ClusterMembership::index).orElse(index)
                                  : index;
                    boolean retired = host.spec().membership().map(ClusterMembership::retired).orElse(false);
                    var clusterControllerContainer = new ClusterControllerContainer(clusterControllers, ccIndex, multitenant, deployState, retired);
                    clusterControllerContainer.setHostResource(host);
                    clusterControllerContainer.initService(deployState.getDeployLogger());
                    clusterControllerContainer.setProp("clustertype", "admin");
                    containers.add(clusterControllerContainer);
                    ++index;
                }
            }
            clusterControllers.addContainers(containers);
            return clusterControllers;
        }

        private void addClusterControllerComponentsForThisCluster(ClusterControllerContainerCluster clusterControllers,
                                                                  ContentCluster contentCluster) {
            int index = 0;
            for (var container : clusterControllers.getContainers()) {
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

    private ContentCluster(AbstractConfigProducer<?> parent, String clusterId,
                           Map<String, NewDocumentType> documentDefinitions,
                           Set<NewDocumentType> globallyDistributedDocuments,
                           String routingSelection, Zone zone, boolean isHosted) {
        super(parent, clusterId);
        this.isHosted = isHosted;
        this.clusterId = clusterId;
        this.documentDefinitions = documentDefinitions;
        this.globallyDistributedDocuments = globallyDistributedDocuments;
        this.documentSelection = routingSelection;
        this.zone = zone;
    }

    public ClusterSpec.Id id() { return ClusterSpec.Id.from(clusterId); }

    public void prepare(DeployState deployState) {
        search.prepare();

        if (clusterControllers != null)
            clusterControllers.prepare(deployState);
    }

    /** Returns cluster controllers if this is multitenant, null otherwise */
    public ClusterControllerContainerCluster getClusterControllers() { return clusterControllers; }

    public DistributionMode getDistributionMode() {
        if (distributionMode != null) return distributionMode;
        return getPersistence().getDefaultDistributionMode();
    }

    public static String getClusterId(ModelElement clusterElem) {
        String clusterId = clusterElem.stringAttribute("id");
        return clusterId != null ? clusterId : "content";
    }

    public String getName() { return clusterId; }

    public String getRoutingSelector() { return documentSelection; }

    public DistributorCluster getDistributorNodes() { return distributorNodes; }

    public StorageCluster getStorageNodes() { return storageNodes; }

    public ClusterControllerConfig getClusterControllerConfig() { return clusterControllerConfig; }

    public PersistenceEngine.PersistenceFactory getPersistence() { return persistenceFactory; }

    /**
     * The list of documentdefinitions declared at the cluster level.
     *
     * @return the set of documenttype names
     */
    public Map<String, NewDocumentType> getDocumentDefinitions() { return documentDefinitions; }

    public boolean isGloballyDistributed(NewDocumentType docType) {
        return globallyDistributedDocuments.contains(docType);
    }

    public final ContentSearchCluster getSearch() { return search; }

    public Redundancy redundancy() { return redundancy; }
    public ContentCluster setRedundancy(Redundancy redundancy) {
        this.redundancy = redundancy;
        return this;
    }

    @Override
    public void getConfig(MessagetyperouteselectorpolicyConfig.Builder builder) {
        if ( ! getSearch().hasIndexedCluster()) return;
        builder.defaultroute(com.yahoo.vespa.model.routing.DocumentProtocol.getDirectRouteName(getConfigId()))
               .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                              .messagetype(DocumentProtocol.MESSAGE_PUTDOCUMENT)
                              .name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId())))
               .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                              .messagetype(DocumentProtocol.MESSAGE_REMOVEDOCUMENT)
                              .name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId())))
               .route(new MessagetyperouteselectorpolicyConfig.Route.Builder()
                              .messagetype(DocumentProtocol.MESSAGE_UPDATEDOCUMENT)
                              .name(com.yahoo.vespa.model.routing.DocumentProtocol.getIndexedRouteName(getConfigId())));
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
        // Telling the controller whether we actually _have_ global document types lets
        // it selectively enable or disable constraints that aren't needed when these
        // are not are present, even if full protocol and backend support is enabled
        // for multiple bucket spaces. Basically, if you don't use it, you don't
        // pay for it.
        builder.cluster_has_global_document_types(!globallyDistributedDocuments.isEmpty());
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

    public boolean isHosted() {
        return isHosted;
    }

    @Override
    public void validate() throws Exception {
        super.validate();
        if (search.usesHierarchicDistribution() && !isHosted) {
            // validate manually configured groups
            new IndexedHierarchicDistributionValidator(search.getClusterName(), rootGroup, redundancy, search.getIndexed().getTuning().dispatch.getDispatchPolicy()).validate();
        }
        new ReservedDocumentTypeNameValidator().validate(documentDefinitions);
        new GlobalDistributionValidator().validate(documentDefinitions, globallyDistributedDocuments);
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
                        removedtags("partofsum"));
    }

    private static final String DEFAULT_BUCKET_SPACE = "default";
    private static final String GLOBAL_BUCKET_SPACE = "global";

    private String bucketSpaceOfDocumentType(NewDocumentType docType) {
        return (isGloballyDistributed(docType) ? GLOBAL_BUCKET_SPACE : DEFAULT_BUCKET_SPACE);
    }

    public AllClustersBucketSpacesConfig.Cluster.Builder clusterBucketSpaceConfigBuilder() {
        AllClustersBucketSpacesConfig.Cluster.Builder builder = new AllClustersBucketSpacesConfig.Cluster.Builder();
        for (NewDocumentType docType : getDocumentDefinitions().values()) {
            AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder typeBuilder = new AllClustersBucketSpacesConfig.Cluster.DocumentType.Builder();
            typeBuilder.bucketSpace(bucketSpaceOfDocumentType(docType));
            builder.documentType(docType.getName(), typeBuilder);
        }
        return builder;
    }

    @Override
    public void getConfig(BucketspacesConfig.Builder builder) {
        for (NewDocumentType docType : getDocumentDefinitions().values()) {
            BucketspacesConfig.Documenttype.Builder docTypeBuilder = new BucketspacesConfig.Documenttype.Builder();
            docTypeBuilder.name(docType.getName());
            docTypeBuilder.bucketspace(bucketSpaceOfDocumentType(docType));
            builder.documenttype(docTypeBuilder);
        }
    }

    @Override
    public void getConfig(DistributionConfig.Builder builder) {
        DistributionConfig.Cluster.Builder clusterBuilder = new DistributionConfig.Cluster.Builder();
        StorDistributionConfig.Builder storDistributionBuilder = new StorDistributionConfig.Builder();
        getConfig(storDistributionBuilder);
        StorDistributionConfig config = storDistributionBuilder.build();

        clusterBuilder.active_per_leaf_group(config.active_per_leaf_group());
        clusterBuilder.ready_copies(config.ready_copies());
        clusterBuilder.redundancy(config.redundancy());
        clusterBuilder.initial_redundancy(config.initial_redundancy());

        for (StorDistributionConfig.Group group : config.group()) {
            DistributionConfig.Cluster.Group.Builder groupBuilder = new DistributionConfig.Cluster.Group.Builder();
            groupBuilder.index(group.index())
                        .name(group.name())
                        .capacity(group.capacity())
                        .partitions(group.partitions());

            for (var node : group.nodes()) {
                DistributionConfig.Cluster.Group.Nodes.Builder nodesBuilder = new DistributionConfig.Cluster.Group.Nodes.Builder();
                nodesBuilder.index(node.index())
                            .retired(node.retired());

                groupBuilder.nodes(nodesBuilder);
            }

            clusterBuilder.group(groupBuilder);
        }

        builder.cluster(getConfigId(), clusterBuilder);
    }

    /**
     * Mark whether the config emitted by this cluster currently should be applied by clients already running with
     * a previous generation of it only by restarting the consuming processes.
     */
    public void setDeferChangesUntilRestart(boolean deferChangesUntilRestart) {
    }

}
