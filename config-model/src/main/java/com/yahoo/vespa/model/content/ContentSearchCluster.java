// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.content;

import com.yahoo.config.model.api.ModelContext;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.DispatchConfig;
import com.yahoo.vespa.config.search.DispatchNodesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.model.builder.xml.dom.DomSearchTuningBuilder;
import com.yahoo.vespa.model.builder.xml.dom.ModelElement;
import com.yahoo.vespa.model.builder.xml.dom.VespaDomBuilder;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.IndexingDocproc;
import com.yahoo.vespa.model.search.NodeSpec;
import com.yahoo.vespa.model.search.SchemaDefinitionXMLHandler;
import com.yahoo.vespa.model.search.SearchCluster;
import com.yahoo.vespa.model.search.SearchNode;
import com.yahoo.vespa.model.search.TransactionLogServer;
import com.yahoo.vespa.model.search.Tuning;
import org.w3c.dom.Element;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Objects;
import java.util.function.Predicate;

/**
 * Encapsulates the various options for search in a content model.
 * Wraps a search cluster from com.yahoo.vespa.model.search.
 */
public class ContentSearchCluster extends TreeConfigProducer<AnyConfigProducer> implements
        ProtonConfig.Producer,
        DispatchNodesConfig.Producer,
        DispatchConfig.Producer,
        Redundancy.Provider
{

    private static final int DEFAULT_DOC_STORE_COMPRESSION_LEVEL = 3;
    private static final double DEFAULT_DISK_BLOAT = 0.25;

    private final boolean flushOnShutdown;
    private final Boolean syncTransactionLog;

    /** The single, indexed search cluster this sets up (supporting multiple document types), or null if none */
    private IndexedSearchCluster searchCluster;
    private final IndexingDocproc indexingDocproc;
    private Redundancy redundancy;

    private final String clusterName;
    private final Map<String, NewDocumentType> documentDefinitions;
    private final Set<NewDocumentType> globallyDistributedDocuments;
    private Double visibilityDelay = 0.0;

    /** The search nodes of this if it does not have an indexed cluster */
    private final List<SearchNode> nonIndexed = new ArrayList<>();

    private final Map<StorageGroup, NodeSpec> groupToSpecMap = new LinkedHashMap<>();
    private ResourceLimits resourceLimits;
    private final ProtonConfig.Indexing.Optimize.Enum feedSequencerType;
    private final double defaultFeedConcurrency;
    private final double defaultFeedNiceness;
    private final boolean forwardIssuesToQrs;
    private final int defaultMaxCompactBuffers;

    /** Whether the nodes of this cluster also hosts a container cluster in a hosted system */
    private final double fractionOfMemoryReserved;

    public static class Builder extends VespaDomBuilder.DomConfigProducerBuilderBase<ContentSearchCluster> {

        private final Map<String, NewDocumentType> documentDefinitions;
        private final Set<NewDocumentType> globallyDistributedDocuments;
        private final double fractionOfMemoryReserved;
        private final ResourceLimits resourceLimits;

        public Builder(Map<String, NewDocumentType> documentDefinitions,
                       Set<NewDocumentType> globallyDistributedDocuments,
                       double fractionOfMemoryReserved, ResourceLimits resourceLimits)
        {
            this.documentDefinitions = documentDefinitions;
            this.globallyDistributedDocuments = globallyDistributedDocuments;
            this.fractionOfMemoryReserved = fractionOfMemoryReserved;
            this.resourceLimits = resourceLimits;
        }

        @Override
        protected ContentSearchCluster doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
            ModelElement clusterElem = new ModelElement(producerSpec);
            String clusterName = ContentCluster.getClusterId(clusterElem);
            Boolean flushOnShutdownElem = clusterElem.childAsBoolean("engine.proton.flush-on-shutdown");
            Boolean syncTransactionLog = clusterElem.childAsBoolean("engine.proton.sync-transactionlog");

            var search = new ContentSearchCluster(ancestor, clusterName, deployState.getProperties().featureFlags(),
                                                  documentDefinitions, globallyDistributedDocuments,
                                                  getFlushOnShutdown(flushOnShutdownElem), syncTransactionLog,
                                                  fractionOfMemoryReserved);

            ModelElement tuning = clusterElem.childByPath("engine.proton.tuning");
            if (tuning != null) {
                search.setTuning(new DomSearchTuningBuilder().build(deployState, search, tuning.getXml()));
            }
            search.setResourceLimits(resourceLimits);

            buildSearchCluster(deployState, clusterElem, clusterName, search);
            return search;
        }

        private boolean getFlushOnShutdown(Boolean flushOnShutdownElem) {
            return Objects.requireNonNullElse(flushOnShutdownElem, true);
        }

        private Double getQueryTimeout(ModelElement clusterElem) {
            return clusterElem.childAsDouble("engine.proton.query-timeout");
        }

        private void buildSearchCluster(DeployState deployState, ModelElement clusterElem,
                                        String clusterName, ContentSearchCluster search) {
            ModelElement docElem = clusterElem.child("documents");
            if (docElem == null) return;

            Double visibilityDelay = clusterElem.childAsDouble("engine.proton.visibility-delay");
            if (visibilityDelay != null) {
                search.setVisibilityDelay(visibilityDelay);
            }

            var isc = new IndexedSearchCluster(search, clusterName, 0, search, deployState.featureFlags());
            search.addSearchCluster(deployState, isc, getQueryTimeout(clusterElem), docElem.subElements("document"));
        }
    }

    private static ProtonConfig.Indexing.Optimize.Enum convertFeedSequencerType(String sequencerType) {
        try {
            return ProtonConfig.Indexing.Optimize.Enum.valueOf(sequencerType);
        } catch (Throwable t) {
            return ProtonConfig.Indexing.Optimize.Enum.LATENCY;
        }
    }

    private ContentSearchCluster(TreeConfigProducer<?> parent,
                                 String clusterName,
                                 ModelContext.FeatureFlags featureFlags,
                                 Map<String, NewDocumentType> documentDefinitions,
                                 Set<NewDocumentType> globallyDistributedDocuments,
                                 boolean flushOnShutdown,
                                 Boolean syncTransactionLog,
                                 double fractionOfMemoryReserved)
    {
        super(parent, "search");
        this.indexingDocproc = new IndexingDocproc();
        this.clusterName = clusterName;
        this.documentDefinitions = documentDefinitions;
        this.globallyDistributedDocuments = globallyDistributedDocuments;
        this.flushOnShutdown = flushOnShutdown;
        this.syncTransactionLog = syncTransactionLog;

        this.fractionOfMemoryReserved = fractionOfMemoryReserved;
        this.feedSequencerType = convertFeedSequencerType(featureFlags.feedSequencerType());
        this.defaultFeedConcurrency = featureFlags.feedConcurrency();
        this.defaultFeedNiceness = featureFlags.feedNiceness();
        this.forwardIssuesToQrs = featureFlags.forwardIssuesAsErrors();
        this.defaultMaxCompactBuffers = featureFlags.maxCompactBuffers();
    }

    public void setVisibilityDelay(double delay) {
        this.visibilityDelay=delay;
        if (searchCluster != null) {
            searchCluster.setVisibilityDelay(delay);
        }
    }

    private void addSearchCluster(DeployState deployState, IndexedSearchCluster cluster, Double queryTimeout, List<ModelElement> documentDefs) {
        addSchemas(deployState, documentDefs, cluster);

        if (queryTimeout != null) {
            cluster.setQueryTimeout(queryTimeout);
        }
        cluster.deriveFromSchemas(deployState);
        if ( ! cluster.schemas().values().stream().allMatch(schemaInfo -> schemaInfo.getIndexMode() == SchemaInfo.IndexMode.STORE_ONLY)) {
            addCluster(cluster);
        }
    }


    private void addSchemas(DeployState deployState, List<ModelElement> searchDefs, SearchCluster sc) {
        for (ModelElement e : searchDefs) {
            SchemaDefinitionXMLHandler schemaDefinitionXMLHandler = new SchemaDefinitionXMLHandler(e);
            Schema schema = schemaDefinitionXMLHandler.findResponsibleSchema(deployState.getSchemas());
            if (schema == null)
                throw new IllegalArgumentException("Schema '" + schemaDefinitionXMLHandler.getName() + "' referenced in " +
                                                   this + " does not exist");
            if (schema.isDocumentsOnly()) continue;

            sc.add(new SchemaInfo(schema, e.stringAttribute("mode"), deployState.rankProfileRegistry(), null));
        }
    }

    private void addCluster(IndexedSearchCluster sc) {
        if (searchCluster != null) {
            throw new IllegalArgumentException("Duplicate indexed cluster '" + searchCluster.getClusterName() + "'");
        }
        searchCluster = sc;
    }

    /**
     * Returns whether the schemas in this cluster use streaming mode.
     *
     * @return True if this cluster only has schemas with streaming mode, False if it only has schemas
     *         with indexing, null if it has both or none.
     */
    public Boolean isStreaming() {
        if (searchCluster == null) return false;
        boolean hasStreaming = searchCluster.hasStreaming();
        if (searchCluster.hasIndexed() == hasStreaming) return null;
        return hasStreaming;
    }

    public boolean hasStreaming() {
        return (searchCluster != null) && searchCluster.hasStreaming();
    }

    public boolean hasIndexed() {
        return (searchCluster != null) && searchCluster.hasIndexed();
    }

    public List<SearchNode> getSearchNodes() {
        return (searchCluster != null) ? searchCluster.getSearchNodes() : nonIndexed;
    }

    public void addSearchNode(DeployState deployState, ContentNode node, StorageGroup parentGroup, ModelElement element) {
        TreeConfigProducer<AnyConfigProducer> parent = (searchCluster != null) ? searchCluster : this;

        NodeSpec spec = getNextSearchNodeSpec(parentGroup);
        SearchNode searchNode;
        TransactionLogServer tls;
        if (element == null) {
            searchNode = SearchNode.create(parent, "" + node.getDistributionKey(), node.getDistributionKey(), spec,
                                           clusterName, node, flushOnShutdown, tuning, resourceLimits, deployState.isHosted(),
                                           fractionOfMemoryReserved, deployState.featureFlags());
            searchNode.setHostResource(node.getHostResource());
            searchNode.initService(deployState);

            tls = new TransactionLogServer(searchNode, clusterName, syncTransactionLog);
            tls.setHostResource(searchNode.getHostResource());
            tls.initService(deployState);
        } else {
            searchNode = new SearchNode.Builder(""+node.getDistributionKey(), spec, clusterName, node, flushOnShutdown,
                                                tuning, resourceLimits, fractionOfMemoryReserved)
                    .build(deployState, parent, element.getXml());
            tls = new TransactionLogServer.Builder(clusterName, syncTransactionLog).build(deployState, searchNode, element.getXml());
        }
        searchNode.setTls(tls);
        if (searchCluster != null) {
            searchCluster.addSearcher(searchNode);
        } else {
            nonIndexed.add(searchNode);
        }
    }

    /** Translates group ids to continuous 0-base "row" id integers */
    private NodeSpec getNextSearchNodeSpec(StorageGroup parentGroup) {
        NodeSpec spec = groupToSpecMap.get(parentGroup);
        if (spec == null) {
            spec = new NodeSpec(groupToSpecMap.size(), 0);
        } else {
            spec = new NodeSpec(spec.groupIndex(), spec.partitionId() + 1);
        }
        groupToSpecMap.put(parentGroup, spec);
        return spec;
    }

    private Tuning tuning;

    public void setTuning(Tuning tuning) { this.tuning = tuning; }

    private void setResourceLimits(ResourceLimits resourceLimits) {
        this.resourceLimits = resourceLimits;
    }

    public boolean usesHierarchicDistribution() {
        return searchCluster != null && groupToSpecMap.size() > 1;
    }

    public void handleRedundancy(Redundancy redundancy) {
        this.redundancy = redundancy;
    }

    public List<NewDocumentType> getDocumentTypesWithStreamingCluster() { return documentTypes(this::hasIndexingModeStreaming); }
    public List<NewDocumentType> getDocumentTypesWithIndexedCluster() { return documentTypes(this::hasIndexingModeIndexed); }
    public List<NewDocumentType> getDocumentTypesWithStoreOnly() { return documentTypes(this::hasIndexingModeStoreOnly); }

    private List<NewDocumentType> documentTypes(Predicate<NewDocumentType> filter) {
        return documentDefinitions.values().stream()
                .filter(filter)
                .toList();
    }

    private boolean hasIndexingModeStreaming(NewDocumentType type) {
        if (searchCluster == null) return false;
        var schemaInfo = searchCluster.schemas().get(type.getName());
        return (schemaInfo != null) && (schemaInfo.getIndexMode() == SchemaInfo.IndexMode.STREAMING);
    }

    private boolean hasIndexingModeIndexed(NewDocumentType type) {
        if (searchCluster == null) return false;
        var schemaInfo = searchCluster.schemas().get(type.getName());
        return (schemaInfo != null) && (schemaInfo.getIndexMode() == SchemaInfo.IndexMode.INDEX);
    }

    private boolean hasIndexingModeStoreOnly(NewDocumentType type) {
        return !hasIndexingModeStreaming(type) && !hasIndexingModeIndexed(type);
    }

    @Override
    public void getConfig(ProtonConfig.Builder builder) {
        boolean hasAnyNonIndexedSchema = false;
        for (NewDocumentType type : TopologicalDocumentTypeSorter.sort(documentDefinitions.values())) {
            ProtonConfig.Documentdb.Builder ddbB = new ProtonConfig.Documentdb.Builder();
            String docTypeName = type.getFullName().getName();
            boolean globalDocType = isGloballyDistributed(type);
            ddbB.inputdoctypename(docTypeName)
                .configid(getConfigId())
                .visibilitydelay(visibilityDelay)
                .global(globalDocType);
            ddbB.allocation.max_compact_buffers(defaultMaxCompactBuffers);

            if (hasIndexingModeStreaming(type)) {
                hasAnyNonIndexedSchema = true;
                searchCluster.fillDocumentDBConfig(type.getFullName().getName(), ddbB);
                ddbB.mode(ProtonConfig.Documentdb.Mode.Enum.STREAMING);
            } else if (hasIndexingModeIndexed(type)) {
                searchCluster.fillDocumentDBConfig(type.getFullName().getName(), ddbB);
            } else {
                hasAnyNonIndexedSchema = true;
                ddbB.mode(ProtonConfig.Documentdb.Mode.Enum.STORE_ONLY);
            }
            if (globalDocType) {
                ddbB.visibilitydelay(0.0);
            }
            builder.documentdb(ddbB);
        }

        if (hasAnyNonIndexedSchema) {
            builder.feeding.concurrency(Math.min(1.0, defaultFeedConcurrency*2));
        } else {
            builder.feeding.concurrency(defaultFeedConcurrency);
        }
        builder.feeding.niceness(defaultFeedNiceness);
        builder.flush.memory.diskbloatfactor(DEFAULT_DISK_BLOAT);
        builder.flush.memory.each.diskbloatfactor(DEFAULT_DISK_BLOAT);
        builder.summary.log.chunk.compression.level(DEFAULT_DOC_STORE_COMPRESSION_LEVEL);
        builder.summary.log.compact.compression.level(DEFAULT_DOC_STORE_COMPRESSION_LEVEL);
        builder.forward_issues(forwardIssuesToQrs);

        int numDocumentDbs = builder.documentdb.size();
        builder.initialize(new ProtonConfig.Initialize.Builder().threads(numDocumentDbs + 1));

        if (resourceLimits != null) resourceLimits.getConfig(builder);

        if (tuning != null) {
            tuning.getConfig(builder);
        }
        if (redundancy != null) {
            redundancy.getConfig(builder);
        }

        builder.indexing.optimize(feedSequencerType);
        setMaxFlushed(builder);
    }

    private void setMaxFlushed(ProtonConfig.Builder builder) {
        // maxflushed should be moved down to proton
        double concurrency = builder.feeding.build().concurrency();
        if (concurrency > defaultFeedConcurrency) {
            int maxFlushes = (int)Math.ceil(4 * concurrency);
            builder.index.maxflushed(maxFlushes);
        }
    }

    private boolean isGloballyDistributed(NewDocumentType docType) {
        return globallyDistributedDocuments.contains(docType);
    }

    @Override
    public void getConfig(DispatchNodesConfig.Builder builder) {
        if (searchCluster != null) {
            searchCluster.getConfig(builder);
        }
    }

    @Override
    public void getConfig(DispatchConfig.Builder builder) {
        if (searchCluster != null) {
            searchCluster.getConfig(builder);
        }
    }
    public IndexedSearchCluster getSearchCluster() { return searchCluster; }
    public boolean hasSearchCluster()       { return searchCluster != null; }
    public IndexingDocproc getIndexingDocproc() { return indexingDocproc; }
    public String getClusterName() { return clusterName; }

    @Override
    public String toString() { return "content cluster '" + clusterName + "'"; }

    public Redundancy redundancy() { return redundancy; }

}
