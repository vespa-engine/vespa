// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.component.ComponentId;
import com.yahoo.component.ComponentSpecification;
import com.yahoo.component.chain.model.ChainSpecification;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.config.model.ApplicationConfigProducerRoot;
import com.yahoo.config.model.ConfigModel;
import com.yahoo.config.model.ConfigModelContext;
import com.yahoo.config.model.ConfigModelRepo;
import com.yahoo.config.model.admin.AdminModel;
import com.yahoo.config.model.builder.xml.ConfigModelBuilder;
import com.yahoo.config.model.builder.xml.ConfigModelId;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.vespa.model.AbstractService;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.admin.Admin;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.ApplicationContainerCluster;
import com.yahoo.vespa.model.container.ApplicationContainer;
import com.yahoo.vespa.model.container.ContainerModel;
import com.yahoo.vespa.model.container.docproc.ContainerDocproc;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.container.docproc.DocprocChains;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.IndexingDocproc;
import com.yahoo.vespa.model.search.IndexingDocprocChain;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.SearchNode;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * The config model from a content tag in services.
 * This consists mostly of a ContentCluster.
 *
 * @author baldersheim
 */
public class Content extends ConfigModel {

    private static final String DOCPROC_RESERVED_NAME = "docproc";

    private ContentCluster cluster;
    private Optional<ApplicationContainerCluster> ownedIndexingCluster = Optional.empty();

    // Dependencies to other models
    private final AdminModel adminModel;

    // to find or add the docproc container and supplement cluster controllers with clusters having less than 3 nodes
    private final Collection<ContainerModel> containers;

    @SuppressWarnings("UnusedDeclaration") // Created by reflection in ConfigModelRepo
    public Content(ConfigModelContext modelContext, AdminModel adminModel, Collection<ContainerModel> containers) {
        super(modelContext);
        modelContext.getParentProducer().getRoot();
        this.adminModel = adminModel;
        this.containers = containers;
    }

    public ContentCluster getCluster() { return cluster; }

    /**
     * Returns indexing cluster implicitly created by this,
     * or empty if an explicit cluster is used (or if called before the build phase)
     */
    public Optional<ApplicationContainerCluster> ownedIndexingCluster() { return ownedIndexingCluster; }

    private static boolean containsIndexingChain(ComponentRegistry<DocprocChain> allChains, ChainSpecification chainSpec) {
        if (IndexingDocprocChain.NAME.equals(chainSpec.componentId.stringValue())) return true;

        ChainSpecification.Inheritance inheritance = chainSpec.inheritance;
        for (ComponentSpecification parentComponentSpec : inheritance.chainSpecifications) {
            ChainSpecification parentSpec = getChainSpec(allChains, parentComponentSpec);
            if (containsIndexingChain(allChains, parentSpec)) return true;
        }

        return false;
    }

    private static ChainSpecification getChainSpec(ComponentRegistry<DocprocChain> allChains, ComponentSpecification componentSpec) {
        DocprocChain docprocChain = allChains.getComponent(componentSpec);
        if (docprocChain == null) throw new IllegalArgumentException("Chain '" + componentSpec + "' not found.");

        return docprocChain.getChainSpecification();
    }

    private static void addIndexingChain(ContainerCluster<?> containerCluster) {
        DocprocChain chainAlreadyPresent = containerCluster.getDocprocChains().allChains().
                getComponent(new ComponentId(IndexingDocprocChain.NAME));
        if (chainAlreadyPresent != null) {
            if (chainAlreadyPresent instanceof IndexingDocprocChain) return;
            throw new IllegalArgumentException("A docproc chain may not have the ID '" +
                                               IndexingDocprocChain.NAME + ", since this is reserved by Vespa. Please use a different ID.");
        }

        containerCluster.getDocprocChains().add(new IndexingDocprocChain());
    }

    private static ContainerCluster<?> getContainerWithSearch(Collection<ContainerModel> containers) {
        for (ContainerModel container : containers)
            if (container.getCluster().getSearch() != null)
                return container.getCluster();
        return null;
    }

    private static void checkThatExplicitIndexingChainInheritsCorrectly(ComponentRegistry<DocprocChain> allChains,
                                                                        ChainSpecification chainSpec) {
        ChainSpecification.Inheritance inheritance = chainSpec.inheritance;
        for (ComponentSpecification componentSpec : inheritance.chainSpecifications) {
            ChainSpecification parentSpec = getChainSpec(allChains, componentSpec);
            if (containsIndexingChain(allChains, parentSpec)) return;
        }
        throw new IllegalArgumentException("Docproc chain '" + chainSpec.componentId +
                                           "' must inherit from the 'indexing' chain");
    }

    public static List<Content> getContent(ConfigModelRepo pc) {
        List<Content> contents = new ArrayList<>();
        for (ConfigModel model : pc.asMap().values())
            if (model instanceof Content)
                contents.add((Content)model);
        return contents;
    }

    public static List<SearchCluster> getSearchClusters(ConfigModelRepo pc) {
        List<SearchCluster> clusters = new ArrayList<>();
        for (ContentCluster c : getContentClusters(pc))
            clusters.addAll(c.getSearch().getClusters().values());
        return clusters;
    }

    public static List<ContentCluster> getContentClusters(ConfigModelRepo pc) {
       List<ContentCluster> clusters = new ArrayList<>();
        for (Content c : getContent(pc))
            clusters.add(c.getCluster());
        return clusters;
    }

    @Override
    public void prepare(ConfigModelRepo models, DeployState deployState) {
        if (cluster.getRootGroup().useCpuSocketAffinity()) {
            setCpuSocketAffinity();
        }
        if (cluster.getRootGroup().getMmapNoCoreLimit().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setMMapNoCoreLimit(cluster.getRootGroup().getMmapNoCoreLimit().get());
            }
        }
        if (cluster.getRootGroup().getCoreOnOOM().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setCoreOnOOM(cluster.getRootGroup().getCoreOnOOM().get());
            }
        }
        if (cluster.getRootGroup().getNoVespaMalloc().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setNoVespaMalloc(cluster.getRootGroup().getNoVespaMalloc().get());
            }
        }
        if (cluster.getRootGroup().getVespaMalloc().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setVespaMalloc(cluster.getRootGroup().getVespaMalloc().get());
            }
        }
        if (cluster.getRootGroup().getVespaMallocDebug().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setVespaMallocDebug(cluster.getRootGroup().getVespaMallocDebug().get());
            }
        }
        if (cluster.getRootGroup().getVespaMallocDebugStackTrace().isPresent()) {
            for (AbstractService s : cluster.getSearch().getSearchNodes()) {
                s.setVespaMallocDebugStackTrace(cluster.getRootGroup().getVespaMallocDebugStackTrace().get());
            }
        }
    }

    private void setCpuSocketAffinity() {
        // Currently only distribute affinity for search nodes
        AbstractService.distributeCpuSocketAffinity(cluster.getSearch().getSearchNodes());
    }

    public static class Builder extends ConfigModelBuilder<Content> {
    
        public static final List<ConfigModelId> configModelIds = Collections.singletonList(ConfigModelId.fromName("content"));
    
        public Builder() {
            super(Content.class);
        }
    
        @Override
        public List<ConfigModelId> handlesElements() {
            return configModelIds;
        }
    
        @Override
        public void doBuild(Content content, Element xml, ConfigModelContext modelContext) {
            Admin admin = content.adminModel != null ? content.adminModel.getAdmin() : null; // This is null in tests only
            content.cluster = new ContentCluster.Builder(admin).build(content.containers, modelContext, xml);
            buildIndexingClusters(content, modelContext,
                                  (ApplicationConfigProducerRoot)modelContext.getParentProducer());
        }

        /** Select/creates and initializes the indexing cluster coupled to this */
        private void buildIndexingClusters(Content content, ConfigModelContext modelContext,
                                           ApplicationConfigProducerRoot root) {
            var search = content.getCluster().getSearch();
            if (!search.getIndexingDocproc().isPresent()) {
                return;
            }
            var indexingDocproc = search.getIndexingDocproc().get();
            if (indexingDocproc.hasExplicitCluster()) {
                setExistingIndexingCluster(content, indexingDocproc, content.containers);
            } else {
                if (search.hasIndexedCluster()) {
                    setContainerAsIndexingCluster(search.getIndexed(), content, modelContext, root);
                }
            }
        }

        private void setContainerAsIndexingCluster(IndexedSearchCluster indexedSearchCluster,
                                                   Content content,
                                                   ConfigModelContext modelContext,
                                                   ApplicationConfigProducerRoot root) {
            if (content.containers.isEmpty()) {
                createImplicitIndexingCluster(indexedSearchCluster, content, modelContext, root);
            } else {
                ContainerCluster<?> targetCluster = getContainerWithDocproc(content.containers);
                if (targetCluster == null)
                    targetCluster = getContainerWithSearch(content.containers);
                if (targetCluster == null)
                    targetCluster = content.containers.iterator().next().getCluster();

                addDocproc(targetCluster);
                var indexingDocproc = indexedSearchCluster.getIndexingDocproc();
                indexingDocproc.setClusterName(targetCluster.getName());
                addIndexingChainsTo(targetCluster, content, indexingDocproc);
            }
        }

        private void setExistingIndexingCluster(Content content, IndexingDocproc indexingDocproc, Collection<ContainerModel> containers) {
            String indexingClusterName = indexingDocproc.getClusterName(content.getCluster().getName());
            ContainerModel containerModel = findByName(indexingClusterName, containers);
            if (containerModel == null)
                throw new IllegalArgumentException("Content cluster '" + content.getCluster().getName() + "' refers to docproc " +
                                                   "cluster '" + indexingClusterName + "', but this cluster does not exist.");
            addIndexingChainsTo(containerModel.getCluster(), content, indexingDocproc);
        }

        private ContainerModel findByName(String name, Collection<ContainerModel> containers) {
            for (ContainerModel container : containers)
                if (container.getId().equals(name))
                    return container;
            return null;
        }

        private void addIndexingChainsTo(ContainerCluster<?> indexer, Content content, IndexingDocproc indexingDocproc) {
            addIndexingChain(indexer);
            DocprocChain indexingChain;
            ComponentRegistry<DocprocChain> allChains = indexer.getDocprocChains().allChains();
            if (indexingDocproc.hasExplicitChain() && !indexingDocproc.getChainName().equals(IndexingDocprocChain.NAME)) {
                indexingChain = allChains.getComponent(indexingDocproc.getChainName());
                if (indexingChain == null) {
                    throw new IllegalArgumentException(content.getCluster() + " refers to docproc " +
                                                       "chain '" + indexingDocproc.getChainName() +
                                                       "' for indexing, but this chain does not exist");
                }
                else if (indexingChain.getId().getName().equals("default")) {
                    throw new IllegalArgumentException(content.getCluster() + " specifies the chain " +
                                                       "'default' as indexing chain. As the 'default' chain is run by default, " +
                                                       "using it as the indexing chain will run it twice. " +
                                                       "Use a different name for the indexing chain.");
                }
                else {
                    checkThatExplicitIndexingChainInheritsCorrectly(allChains, indexingChain.getChainSpecification());
                }
            } else {
                indexingChain = allChains.getComponent(IndexingDocprocChain.NAME);
            }

            indexingDocproc.setChain(indexingChain);
        }

        private TreeConfigProducer<AnyConfigProducer> getDocProc(ApplicationConfigProducerRoot root) {
            AnyConfigProducer current = root.getChildren().get(DOCPROC_RESERVED_NAME);
            if (current == null) {
                return new SimpleConfigProducer(root, DOCPROC_RESERVED_NAME);
            }
            if (current instanceof TreeConfigProducer t) {
                return t;
            }
            throw new IllegalStateException("ApplicationConfigProducerRoot " + root + " with bad type for " + DOCPROC_RESERVED_NAME + ": " + current.getClass());
        }

        /** Create a new container cluster for indexing and add it to the Vespa model */
        private void createImplicitIndexingCluster(IndexedSearchCluster cluster,
                                                   Content content,
                                                   ConfigModelContext modelContext,
                                                   ApplicationConfigProducerRoot root) {
            String indexerName = cluster.getIndexingDocproc().getClusterName(content.getCluster().getName());
            TreeConfigProducer<AnyConfigProducer> parent = getDocProc(root);
            ApplicationContainerCluster indexingCluster = new ApplicationContainerCluster(parent, "cluster." + indexerName, indexerName, modelContext.getDeployState());
            ContainerModel indexingClusterModel = new ContainerModel(modelContext.withParent(parent).withId(indexingCluster.getSubId()));
            indexingClusterModel.setCluster(indexingCluster);
            modelContext.getConfigModelRepoAdder().add(indexingClusterModel);
            content.ownedIndexingCluster = Optional.of(indexingCluster);

            indexingCluster.addDefaultHandlersWithVip();
            indexingCluster.addAllPlatformBundles();
            indexingCluster.addAccessLog();
            addDocproc(indexingCluster);

            List<ApplicationContainer> nodes = new ArrayList<>();
            int index = 0;
            Set<HostResource> processedHosts = new LinkedHashSet<>();
            for (SearchNode searchNode : cluster.getSearchNodes()) {
                HostResource host = searchNode.getHostResource();
                if (!processedHosts.contains(host)) {
                    String containerName = String.valueOf(searchNode.getDistributionKey());
                    ApplicationContainer docprocService = new ApplicationContainer(indexingCluster, containerName, index,
                                                                                   modelContext.getDeployState());
                    index++;
                    docprocService.useDynamicPorts();
                    docprocService.setHostResource(host);
                    docprocService.initService(modelContext.getDeployState());
                    nodes.add(docprocService);
                    processedHosts.add(host);
                }
            }
            indexingCluster.addContainers(nodes);

            addIndexingChain(indexingCluster);
            cluster.getIndexingDocproc().setChain(indexingCluster.getDocprocChains().allChains().getComponent(IndexingDocprocChain.NAME));
        }

        private ContainerCluster<?> getContainerWithDocproc(Collection<ContainerModel> containers) {
            for (ContainerModel container : containers)
                if (container.getCluster().getDocproc() != null)
                    return container.getCluster();
            return null;
        }

        private void addDocproc(ContainerCluster<?> cluster) {
            if (cluster.getDocproc() == null) {
                DocprocChains chains = new DocprocChains(cluster, "docprocchains");
                ContainerDocproc containerDocproc = new ContainerDocproc(cluster, chains);
                cluster.setDocproc(containerDocproc);
            }
        }

    }

}

