// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.searchdefinition.DocumentOnlySearch;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchConfig.DistributionPolicy;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.vespa.model.container.docproc.DocprocChain;
import com.yahoo.vespa.model.content.DispatchSpec;
import com.yahoo.vespa.model.content.SearchCoverage;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * @author baldersheim
 */
public class IndexedSearchCluster extends SearchCluster
    implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        DispatchConfig.Producer {

    private String indexingClusterName = null; // The name of the docproc cluster to run indexing, by config.
    private String indexingChainName = null;

    private DocprocChain indexingChain; // The actual docproc chain indexing for this.

    private Tuning tuning;
    private SearchCoverage searchCoverage;

    // This is the document selector string as derived from the subscription tag.
    private String routingSelector = null;
    private final List<DocumentDatabase> documentDbs = new LinkedList<>();
    private final UnionConfiguration unionCfg;
    private int maxNodesDownPerFixedRow = 0;

    private int searchableCopies = 1;

    private final DispatchGroup rootDispatch;
    private DispatchSpec dispatchSpec;
    private final List<SearchNode> searchNodes = new ArrayList<>();

    /**
     * Returns the document selector that is able to resolve what documents are to be routed to this search cluster.
     * This string uses the document selector language as defined in the "document" module.
     *
     * @return The document selector.
     */
    public String getRoutingSelector() {
        return routingSelector;
    }

    public IndexedSearchCluster(AbstractConfigProducer<SearchCluster> parent, String clusterName, int index, DeployState deployState) {
        super(parent, clusterName, index);
        unionCfg = new UnionConfiguration(this, documentDbs);
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
     * @return the name of the docproc cluster associated with this
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
     * @param name the name of the docproc cluster associated with this
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
     * @param chain the chain that is to run indexing for this cluster
     * @return this, to allow chaining
     */
    public AbstractSearchCluster setIndexingChain(DocprocChain chain) {
        indexingChain = chain;
        return this;
    }

    public DispatchGroup getRootDispatch() { return rootDispatch; }

    public void addSearcher(SearchNode searcher) {
        searchNodes.add(searcher);
        rootDispatch.addSearcher(searcher);
    }

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

    private void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        ddbB.inputdoctypename(sdoc.getInputDocType())
            .configid(sdoc.getConfigId());
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
                new DocumentSelectionConverter(this.routingSelector);
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
            StringBuilder sb = new StringBuilder(routingSelector);
            while (it.hasNext()) {
                sb.append(" or ").append(it.next());
            }
            routingSelector = sb.toString();
        }
    }

    @Override
    protected void deriveAllSchemas(List<SchemaSpec> localSearches, DeployState deployState) {
        for (SchemaSpec spec : localSearches) {
            com.yahoo.searchdefinition.Search search = spec.getSearchDefinition().getSearch();
            if ( ! (search instanceof DocumentOnlySearch)) {
                DocumentDatabase db = new DocumentDatabase(this, search.getName(),
                                                           new DerivedConfiguration(search,
                                                                                    deployState.getDeployLogger(),
                                                                                    deployState.getProperties(),
                                                                                    deployState.rankProfileRegistry(),
                                                                                    deployState.getQueryProfiles().getRegistry(),
                                                                                    deployState.getImportedModels()));
                // TODO: remove explicit adding of user configs when the complete content model is built using builders.
                db.mergeUserConfigs(spec.getUserConfigs());
                documentDbs.add(db);
            }
        }
    }

    public List<DocumentDatabase> getDocumentDbs() {
        return documentDbs;
    }
    public boolean hasDocumentDB(String name) {
        for (DocumentDatabase db : documentDbs) {
            if (db.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public void setSearchCoverage(SearchCoverage searchCoverage) {
        this.searchCoverage = searchCoverage;
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
    public int getSearchableCopies() {
        return searchableCopies;
    }

    public void setSearchableCopies(int searchableCopies) {
        this.searchableCopies = searchableCopies;
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

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        for (SearchNode node : getSearchNodes()) {
            DispatchConfig.Node.Builder nodeBuilder = new DispatchConfig.Node.Builder();
            nodeBuilder.key(node.getDistributionKey());
            nodeBuilder.group(node.getNodeSpec().groupIndex());
            nodeBuilder.host(node.getHostName());
            nodeBuilder.port(node.getRpcPort());
            builder.node(nodeBuilder);
        }
        if (tuning.dispatch.getTopkProbability() != null) {
            builder.topKProbability(tuning.dispatch.getTopkProbability());
        }
        if (tuning.dispatch.getMinActiveDocsCoverage() != null)
            builder.minActivedocsPercentage(tuning.dispatch.getMinActiveDocsCoverage());
        if (tuning.dispatch.getMinGroupCoverage() != null)
            builder.minGroupCoverage(tuning.dispatch.getMinGroupCoverage());
        if (tuning.dispatch.getDispatchPolicy() != null) {
            switch (tuning.dispatch.getDispatchPolicy()) {
                case ADAPTIVE:
                    builder.distributionPolicy(DistributionPolicy.ADAPTIVE);
                    break;
                case ROUNDROBIN:
                    builder.distributionPolicy(DistributionPolicy.ROUNDROBIN);
                    break;
            }
        }
        if (tuning.dispatch.getMaxHitsPerPartition() != null)
            builder.maxHitsPerNode(tuning.dispatch.getMaxHitsPerPartition());

        builder.maxNodesDownPerGroup(rootDispatch.getMaxNodesDownPerFixedRow());
        builder.searchableCopies(rootDispatch.getSearchableCopies());
        if (searchCoverage != null) {
            if (searchCoverage.getMinimum() != null)
                builder.minSearchCoverage(searchCoverage.getMinimum() * 100.0);
            if (searchCoverage.getMinWaitAfterCoverageFactor() != null)
                builder.minWaitAfterCoverageFactor(searchCoverage.getMinWaitAfterCoverageFactor());
            if (searchCoverage.getMaxWaitAfterCoverageFactor() != null)
                builder.maxWaitAfterCoverageFactor(searchCoverage.getMaxWaitAfterCoverageFactor());
        }
        builder.warmuptime(5.0);
    }

    @Override
    public int getRowBits() { return 8; }

    /**
     * Class used to retrieve combined configuration from multiple document databases.
     * It is not a {@link com.yahoo.config.ConfigInstance.Producer} of those configs,
     * that is handled (by delegating to this) by the {@link IndexedSearchCluster}
     * which is the parent to this. This avoids building the config multiple times.
     */
    public static class UnionConfiguration
            extends AbstractConfigProducer<UnionConfiguration>
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

        private UnionConfiguration(AbstractConfigProducer<?> parent, List<DocumentDatabase> docDbs) {
            super(parent, "union");
            this.docDbs = docDbs;
        }
    }

}
