package com.yahoo.vespa.model.builder.xml.dom;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.documentmodel.NewDocumentType;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.model.content.ContentSearchCluster;
import com.yahoo.vespa.model.content.cluster.ContentCluster;
import com.yahoo.vespa.model.search.IndexedSearchCluster;
import com.yahoo.vespa.model.search.SchemaDefinitionXMLHandler;
import com.yahoo.vespa.model.search.SearchCluster;
import org.w3c.dom.Element;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class DomContentSearchClusterBuilder extends VespaDomBuilder.DomConfigProducerBuilderBase<ContentSearchCluster> {

    private final Map<String, NewDocumentType> documentDefinitions;
    private final Set<NewDocumentType> globallyDistributedDocuments;

    public DomContentSearchClusterBuilder(Map<String, NewDocumentType> documentDefinitions,
                                          Set<NewDocumentType> globallyDistributedDocuments) {
        this.documentDefinitions = documentDefinitions;
        this.globallyDistributedDocuments = globallyDistributedDocuments;
    }

    @Override
    protected ContentSearchCluster doBuild(DeployState deployState, TreeConfigProducer<AnyConfigProducer> ancestor, Element producerSpec) {
        ModelElement clusterElem = new ModelElement(producerSpec);
        String clusterName = ContentCluster.getClusterId(clusterElem);
        Boolean flushOnShutdownElem = clusterElem.childAsBoolean("engine.proton.flush-on-shutdown");
        Boolean syncTransactionLog = clusterElem.childAsBoolean("engine.proton.sync-transactionlog");

        var search = new ContentSearchCluster(ancestor, clusterName, deployState.getProperties().featureFlags(),
                                              documentDefinitions, globallyDistributedDocuments,
                                              getFlushOnShutdown(flushOnShutdownElem, deployState),
                                              syncTransactionLog,
                                              deployState.getProperties().searchNodeInitializerThreads(clusterName));

        ModelElement tuning = clusterElem.childByPath("engine.proton.tuning");
        if (tuning != null) {
            search.setTuning(new DomSearchTuningBuilder().build(deployState, search, tuning.getXml()));
        }

        buildSearchCluster(deployState, clusterElem, clusterName, search);
        return search;
    }

    private boolean getFlushOnShutdown(Boolean flushOnShutdownElem, DeployState deployState) {
        boolean useNewPrepareForRestart = deployState.featureFlags().useNewPrepareForRestart();
        return Objects.requireNonNullElse(flushOnShutdownElem, !deployState.isHosted() || !useNewPrepareForRestart);
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

        var isc = new IndexedSearchCluster(search, clusterName, search, deployState.featureFlags());
        addSearchCluster(search, deployState, isc, getQueryTimeout(clusterElem), docElem.subElements("document"));
    }

    private void addSearchCluster(ContentSearchCluster contentSearchCluster, DeployState deployState,
                                  IndexedSearchCluster cluster, Double queryTimeout, List<ModelElement> documentDefs) {
        addSchemas(deployState, documentDefs, cluster);

        if (queryTimeout != null) {
            cluster.setQueryTimeout(queryTimeout);
        }
        cluster.deriveFromSchemas(deployState);
        if ( ! cluster.schemas().values().stream().allMatch(schemaInfo -> schemaInfo.getIndexMode() == SchemaInfo.IndexMode.STORE_ONLY)) {
            contentSearchCluster.addCluster(cluster);
        }
    }

    private void addSchemas(DeployState deployState, List<ModelElement> schemas, SearchCluster sc) {
        for (ModelElement e : schemas) {
            SchemaDefinitionXMLHandler schemaDefinitionXMLHandler = new SchemaDefinitionXMLHandler(e);
            Schema schema = schemaDefinitionXMLHandler.findResponsibleSchema(deployState.getSchemas());
            if (schema == null)
                throw new IllegalArgumentException("Schema '" + schemaDefinitionXMLHandler.getName() + "' referenced in " +
                                                           this + " does not exist");
            if (schema.isDocumentsOnly()) continue;

            sc.add(new SchemaInfo(schema, e.stringAttribute("mode"), deployState.rankProfileRegistry(), null));
        }
    }

}

