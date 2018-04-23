// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.log.LogLevel;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.searchdefinition.UnproperSearch;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.HostResource;
import com.yahoo.vespa.model.SimpleConfigProducer;
import com.yahoo.vespa.model.container.Container;
import com.yahoo.vespa.model.container.ContainerCluster;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.content.DispatchSpec;
import com.yahoo.vespa.model.content.SearchCoverage;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;

/**
 * @author baldersheim
 */
public class IndexedSearchCluster extends SearchCluster
    implements
        DocumentdbInfoConfig.Producer,
        // TODO consider removing, these only produced by UnionConfiguration and DocumentDatabase?
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        DispatchConfig.Producer
{

    /**
     * Class used to retrieve combined configuration from multiple document databases.
     * It is not a {@link com.yahoo.config.ConfigInstance.Producer} of those configs,
     * that is handled (by delegating to this) by the {@link IndexedSearchCluster}
     * which is the parent to this. This avoids building the config multiple times.
     */
    public static class UnionConfiguration
        extends AbstractConfigProducer
        implements AttributesConfig.Producer {
        private final List<DocumentDatabase> docDbs;

        public void getConfig(IndexInfoConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public void getConfig(IlscriptsConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public void getConfig(RankProfilesConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public UnionConfiguration(AbstractConfigProducer parent, List<DocumentDatabase> docDbs) {
            super(parent, "union");
            this.docDbs = docDbs;
        }
    }

    private static final Logger log = Logger.getLogger(IndexedSearchCluster.class.getName());

    private String indexingClusterName = null; // The name of the docproc cluster to run indexing, by config.
    private String indexingChainName = null;

    private DocprocChain indexingChain; // The actual docproc chain indexing for this.

    private Tuning tuning;
    private SearchCoverage searchCoverage;

    // This is the document selector string as derived from the subscription tag.
    private String routingSelector = null;
    private DocumentSelectionConverter selectionConverter = null;
    private List<DocumentDatabase> documentDbs = new LinkedList<>();
    private final UnionConfiguration unionCfg;
    private int maxNodesDownPerFixedRow = 0;

    private final SimpleConfigProducer dispatchParent;
    private final DispatchGroup rootDispatch;
    private DispatchSpec dispatchSpec;
    private List<SearchNode> searchNodes = new ArrayList<>();

    /**
     * Returns the document selector that is able to resolve what documents are to be routed to this search cluster.
     * This string uses the document selector language as defined in the "document" module.
     *
     * @return The document selector.
     */
    public String getRoutingSelector() {
        return routingSelector;
    }

    public IndexedSearchCluster(AbstractConfigProducer parent, String clusterName, int index) {
        super(parent, clusterName, index);
        unionCfg = new UnionConfiguration(this, documentDbs);
        dispatchParent = new SimpleConfigProducer(this, "dispatchers");
        rootDispatch =  new DispatchGroup(this);
    }

    @Override
    protected IndexingMode getIndexingMode() { return IndexingMode.REALTIME; }

    public final boolean hasExplicitIndexingCluster() {
        return indexingClusterName != null;
    }

    public final boolean hasExplicitIndexingChain() {
        return indexingChainName != null;
    }

    /**
     * Returns the name of the docproc cluster running indexing for this search cluster. This is derived from the
     * services file on initialization, this can NOT be used at runtime to determine indexing chain. When initialization
     * is done, the {@link #getIndexingServiceName()} method holds the actual indexing docproc chain object.
     *
     * @return The name of the docproc cluster associated with this.
     */
    public String getIndexingClusterName() {
        return hasExplicitIndexingCluster() ? indexingClusterName : getClusterName() + ".indexing";
    }

    public String getIndexingChainName() {
        return indexingChainName;
    }

    public void setIndexingChainName(String indexingChainName) {
        this.indexingChainName = indexingChainName;
    }

    /**
     * Sets the name of the docproc cluster running indexing for this search cluster. This is for initial configuration,
     * and will not reflect the actual indexing chain. See {@link #getIndexingClusterName} for more detail.
     *
     * @param name The name of the docproc cluster associated with this.
     */
    public void setIndexingClusterName(String name) {
        indexingClusterName = name;
    }

    public String getIndexingServiceName() {
        return indexingChain.getServiceName();
    }

    /**
     * Sets the docproc chain that will be running indexing for this search cluster. This is set by the
     * {@link com.yahoo.vespa.model.content.Content} model during build.
     *
     * @param chain the chain that is to run indexing for this cluster.
     * @return this, to allow chaining.
     */
    public AbstractSearchCluster setIndexingChain(DocprocChain chain) {
        indexingChain = chain;
        return this;
    }

    public Dispatch addTld(AbstractConfigProducer tldParent, HostResource hostResource) {
        int index = rootDispatch.getDispatchers().size();
        Dispatch tld = Dispatch.createTld(rootDispatch, tldParent, index);
        tld.setHostResource(hostResource);
        tld.initService();
        rootDispatch.addDispatcher(tld);
        return tld;
    }

    /**
     * Make sure to allocate tld with same id as container (i.e if container cluster name is 'foo', with containers
     * with index 0,1,2 the tlds created will get names ../foo.0.tld.0, ../foo.1.tld.1, ../foo.2.tld.2, so that tld config id is
     * stable no matter what changes are done to the number of containers in a container cluster
     * @param tldParent the indexed search cluster the tlds to add should be connected to
     * @param containerCluster the container cluster that should use the tlds created for searching the indexed search cluster above
     */
    public void addTldsWithSameIdsAsContainers(AbstractConfigProducer tldParent, ContainerCluster containerCluster) {
        for (Container container : containerCluster.getContainers()) {
            String containerSubId = container.getSubId();
            if ( ! containerSubId.contains(".")) {
                throw new RuntimeException("Expected container sub id to be of the form string.number");
            }
            int containerIndex = Integer.parseInt(containerSubId.split("\\.")[1]);
            String containerClusterName = containerCluster.getName();
            log.log(LogLevel.DEBUG, "Adding tld with index " + containerIndex + " for content cluster " + this.getClusterName() +
                                    ", container cluster " + containerClusterName + " (container id " + containerSubId +
                                    ") on host " + container.getHostResource().getHostname());
            rootDispatch.addDispatcher(createTld(tldParent, container.getHostResource(), containerClusterName, containerIndex));
        }
    }

    public Dispatch createTld(AbstractConfigProducer tldParent, HostResource hostResource, String containerClusterName, int containerIndex) {
        Dispatch tld = Dispatch.createTldWithContainerIdInName(rootDispatch, tldParent, containerClusterName, containerIndex);
        tld.setHostResource(hostResource);
        tld.initService();
        return tld;
    }

    public DispatchGroup getRootDispatch() { return rootDispatch; }

    public void addSearcher(SearchNode searcher) {
        searchNodes.add(searcher);
        rootDispatch.addSearcher(searcher);
    }

    public List<Dispatch> getTLDs() { return rootDispatch.getDispatchers(); }

    public List<SearchNode> getSearchNodes() { return Collections.unmodifiableList(searchNodes); }
    public int getSearchNodeCount() { return searchNodes.size(); }
    public SearchNode getSearchNode(int index) { return searchNodes.get(index); }
    public void setTuning(Tuning tuning) {
        this.tuning = tuning;
    }
    public Tuning getTuning() { return tuning; }

    public void fillDocumentDBConfig(String documentType, ProtonConfig.Documentdb.Builder builder) {
        for (DocumentDatabase sdoc : documentDbs) {
            if (sdoc.getName().equals(documentType)) {
                fillDocumentDBConfig(sdoc, builder);
                return;
            }
        }
    }

    protected void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        ddbB.inputdoctypename(sdoc.getInputDocType())
            .configid(sdoc.getConfigId())
            .visibilitydelay(getVisibilityDelay());
    }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        for (DocumentDatabase db : documentDbs) {
            DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
            docDb.name(db.getName());
            convertSummaryConfig(db, db, docDb);
            RankProfilesConfig.Builder rpb = new RankProfilesConfig.Builder();
            db.getConfig(rpb);
            addRankProfilesConfig(docDb, new RankProfilesConfig(rpb));
            builder.documentdb(docDb);
        }
    }

    public void setRoutingSelector(String sel) {
        this.routingSelector=sel;
        if (this.routingSelector != null) {
            try {
                this.selectionConverter = new DocumentSelectionConverter(this.routingSelector);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid routing selector: " + e.getMessage());
            }
        }
    }
    /**
     * Create default config if not specified by user.
     * Accept empty strings as user config - it means that all feeds/documents are accepted.
     */
    public void defaultDocumentsConfig() {
        if ((routingSelector == null) && !getDocumentNames().isEmpty()) {
            Iterator<String> it = getDocumentNames().iterator();
            routingSelector = it.next();
            while (it.hasNext()) {
                routingSelector += " or " + it.next();
            }
        }
    }
    protected void deriveAllSearchDefinitions(List<SearchDefinitionSpec> localSearches,
                                              List<com.yahoo.searchdefinition.Search> globalSearches) {
        for (SearchDefinitionSpec spec : localSearches) {
            com.yahoo.searchdefinition.Search search = spec.getSearchDefinition().getSearch();
            if ( ! (search instanceof UnproperSearch)) {
                DocumentDatabase db = new DocumentDatabase(this,
                                                           search.getName(),
                                                           new DerivedConfiguration(search, globalSearches, deployLogger(),
                                                                                    getRoot().getDeployState().rankProfileRegistry(),
                                                                                    getRoot().getDeployState().getQueryProfiles().getRegistry()));
                // TODO: remove explicit adding of user configs when the complete content model is built using builders.
                db.mergeUserConfigs(spec.getUserConfigs());
                documentDbs.add(db);
            }
        }
    }

    public List<DocumentDatabase> getDocumentDbs() {
        return documentDbs;
    }

    public void setSearchCoverage(SearchCoverage searchCoverage) {
        this.searchCoverage = searchCoverage;
    }

    public SearchCoverage getSearchCoverage() {
        return searchCoverage;
    }

    @Override
    public DerivedConfiguration getSdConfig() { return null; }
    
    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        unionCfg.getConfig(builder);
    }
    
    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        unionCfg.getConfig(builder);
    }
    
    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        unionCfg.getConfig(builder);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        unionCfg.getConfig(builder);
    }

    @Override
    protected void exportSdFiles(File toDir) throws IOException { }

    public int getMinNodesPerColumn() { return 0; }

    boolean useFixedRowInDispatch() {
        for (SearchNode node : getSearchNodes()) {
            if (node.getNodeSpec().groupIndex() > 0) {
                return true;
            }
        }
        return false;
    }

    int getMaxNodesDownPerFixedRow() {
        return maxNodesDownPerFixedRow;
    }

    public void setMaxNodesDownPerFixedRow(int value) {
        maxNodesDownPerFixedRow = value;
    }

    public void setDispatchSpec(DispatchSpec dispatchSpec) {
        if (dispatchSpec.getNumDispatchGroups() != null) {
            this.dispatchSpec = new DispatchSpec.Builder().setGroups
                    (DispatchGroupBuilder.createDispatchGroups(getSearchNodes(),
                            dispatchSpec.getNumDispatchGroups())).build();
        } else {
            this.dispatchSpec = dispatchSpec;
        }
    }

    public DispatchSpec getDispatchSpec() {
        return dispatchSpec;
    }

    public boolean useMultilevelDispatchSetup() {
        return dispatchSpec != null && dispatchSpec.getGroups() != null && !dispatchSpec.getGroups().isEmpty();
    }

    public void setupDispatchGroups() {
        if (!useMultilevelDispatchSetup()) {
            return;
        }
        rootDispatch.clearSearchers();
        new DispatchGroupBuilder(dispatchParent, rootDispatch, this).build(dispatchSpec.getGroups(), getSearchNodes());
    }

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        for (SearchNode node : getSearchNodes()) {
            DispatchConfig.Node.Builder nodeBuilder = new DispatchConfig.Node.Builder();
            nodeBuilder.key(node.getDistributionKey());
            nodeBuilder.group(node.getNodeSpec().groupIndex());
            nodeBuilder.host(node.getHostName());
            nodeBuilder.port(node.getRpcPort());
            nodeBuilder.fs4port(node.getDispatchPort());
            if (tuning.dispatch.minActiveDocsCoverage != null)
                builder.minActivedocsPercentage(tuning.dispatch.minActiveDocsCoverage);
            builder.node(nodeBuilder);
        }
    }

    @Override
    protected void assureSdConsistent() { }

    @Override
    public int getRowBits() { return 8; }

}
