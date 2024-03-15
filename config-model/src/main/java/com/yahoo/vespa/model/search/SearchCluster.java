// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.container.QrSearchersConfig;
import com.yahoo.schema.DocumentOnlySchema;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.search.config.ClusterConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a search cluster.
 *
 * @author arnej27959
 */
public abstract class SearchCluster extends TreeConfigProducer<AnyConfigProducer> implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        SchemaInfoConfig.Producer {

    private final String clusterName;
    private int index;
    private Double queryTimeout;
    private Double visibilityDelay = 0.0;
    private final Map<String, SchemaInfo> schemas = new LinkedHashMap<>();
    private final Map<String, DocumentDatabase> documentDbs = new LinkedHashMap<>();
    private final Map<String, AttributesProducer> documentDBProducerForStreaming = new HashMap<>();
    private final List<LegacyStreamingProxy> legacyproxy = new ArrayList<>();

    private static class LegacyStreamingProxy extends TreeConfigProducer<AnyConfigProducer> implements
            AttributesConfig.Producer,
            RankProfilesConfig.Producer,
            RankingConstantsConfig.Producer,
            RankingExpressionsConfig.Producer,
            OnnxModelsConfig.Producer,
            JuniperrcConfig.Producer,
            SummaryConfig.Producer,
            VsmsummaryConfig.Producer,
            VsmfieldsConfig.Producer
    {
        private final DocumentDatabase db;
        LegacyStreamingProxy(TreeConfigProducer<AnyConfigProducer> parent, String clusterName,
                             String schemaName, DerivedConfiguration derived) {
            super(parent, "cluster." + clusterName + "." + schemaName);
            this.db = new DocumentDatabase(this, schemaName, derived);
        }
        @Override public void getConfig(SummaryConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(AttributesConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(OnnxModelsConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(RankingConstantsConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(RankProfilesConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(RankingExpressionsConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(JuniperrcConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(VsmfieldsConfig.Builder builder) { db.getConfig(builder); }
        @Override public void getConfig(VsmsummaryConfig.Builder builder) { db.getConfig(builder); }
    }

    private static class AttributesProducer extends AnyConfigProducer implements AttributesConfig.Producer {
        private final DerivedConfiguration derived;

        AttributesProducer(TreeConfigProducer<AnyConfigProducer> parent, String docType, DerivedConfiguration derived) {
            super(parent, docType);
            this.derived = derived;
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            derived.getConfig(builder, AttributeFields.FieldSet.FAST_ACCESS);
        }
    }

    public SearchCluster(TreeConfigProducer<?> parent, String clusterName, int index) {
        super(parent, "cluster." + clusterName);
        this.clusterName = clusterName;
        this.index = index;
    }

    public String getStorageRouteSpec() { return getClusterName(); }

    public void add(SchemaInfo schema) {
        schemas.put(schema.name(), schema);
    }

    public boolean hasDocumentDB(String name) {
        return documentDbs.containsKey(name);
    }
    public DocumentDatabase getDocumentDB(String name) {
        return documentDbs.get(name);
    }

    public String getConfigId(String name) {
        DocumentDatabase db = documentDbs.get(name);
        return db != null ? db.getConfigId() : "";
    }

    /** Returns the schemas that should be active in this cluster. Note: These are added during processing. */
    public Map<String, SchemaInfo> schemas() { return Collections.unmodifiableMap(schemas); }

    /**
     * Must be called after cluster is built, to derive schema configs.
     * Derives the schemas from the application package.
     * Also stores the document names contained in the schemas.
     */
    public void deriveFromSchemas(DeployState deployState) {
        for (SchemaInfo spec : schemas().values()) {
            if (spec.fullSchema() instanceof DocumentOnlySchema) continue; // TODO verify if this special handling is necessary
            String schemaName = spec.fullSchema().getName();
            var derived = new DerivedConfiguration(deployState, spec.fullSchema(), spec.getIndexMode());
            documentDbs.put(schemaName, new DocumentDatabase(this, schemaName, derived));
            if (spec.getIndexMode() == SchemaInfo.IndexMode.STREAMING) {
                var parent = (TreeConfigProducer<AnyConfigProducer>)getParent();
                documentDBProducerForStreaming.put(schemaName, new AttributesProducer(parent, schemaName, derived));
                legacyproxy.add(new LegacyStreamingProxy(parent, clusterName, schemaName, derived));
            }
        }
    }

    /** Returns the document databases contained in this cluster */
    public List<DocumentDatabase> getDocumentDbs() {
        return documentDbs.values().stream().toList();
    }

    public String getClusterName()              { return clusterName; }
    public final boolean hasStreaming() {
        return schemas().values().stream().anyMatch(schema -> schema.getIndexMode() == SchemaInfo.IndexMode.STREAMING);
    }
    public final boolean hasIndexed() {
        return schemas().values().stream().anyMatch(schema -> schema.getIndexMode() == SchemaInfo.IndexMode.INDEX);
    }

    public final void setQueryTimeout(Double to) { this.queryTimeout = to; }
    public final void setVisibilityDelay(double delay) { this.visibilityDelay = delay; }

    public final Double getVisibilityDelay() { return visibilityDelay; }
    public final Double getQueryTimeout() { return queryTimeout; }
    public final void setClusterIndex(int index) { this.index = index; }
    public final int getClusterIndex() { return index; }

    public void fillDocumentDBConfig(String documentType, ProtonConfig.Documentdb.Builder builder) {
        DocumentDatabase db = documentDbs.get(documentType);
        if (db != null) {
            builder.inputdoctypename(documentType);
            if (db.getDerivedConfiguration().isStreaming()) {
                builder.configid(documentDBProducerForStreaming.get(documentType).getConfigId());
            } else {
                builder.configid(db.getConfigId());
            }
        }
    }

    public QrSearchersConfig.Searchcluster.Builder getQrSearcherConfig() {
        var builder = new QrSearchersConfig.Searchcluster.Builder()
                .name(getClusterName())
                .rankprofiles_configid(getConfigId())
                .storagecluster(new QrSearchersConfig.Searchcluster.Storagecluster.Builder().routespec(getStorageRouteSpec()))
                .indexingmode(hasStreaming() ? QrSearchersConfig.Searchcluster.Indexingmode.STREAMING
                                             : QrSearchersConfig.Searchcluster.Indexingmode.REALTIME);
        for (SchemaInfo spec : schemas().values()) {
            builder.searchdef(spec.fullSchema().getName());
        }
        return builder;
    }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        for (DocumentDatabase db : documentDbs.values()) {
            var docDb = new DocumentdbInfoConfig.Documentdb.Builder()
                    .name(db.getName())
                    .mode(db.getDerivedConfiguration().isStreaming()
                            ? DocumentdbInfoConfig.Documentdb.Mode.Enum.STREAMING
                            : DocumentdbInfoConfig.Documentdb.Mode.Enum.INDEX);
            builder.documentdb(docDb);
        }
    }
    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        new Join(documentDbs.values()).getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        new Join(documentDbs.values()).getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        new Join(documentDbs.values()).getConfig(builder);
    }

    public void getConfig(AttributesConfig.Builder builder) {
        new Join(documentDbs.values()).getConfig(builder);
    }

    public void getConfig(ClusterConfig.Builder builder) {
        builder.clusterId(getClusterIndex());
        builder.clusterName(getClusterName());
        builder.storageRoute(getClusterName());
        builder.configid(getConfigId());
        if (visibilityDelay != null) {
            builder.cacheTimeout(convertVisibilityDelay(visibilityDelay));
        }
        if (hasStreaming()) {
            builder.indexMode(ClusterConfig.IndexMode.Enum.STREAMING);
        } else {
            builder.indexMode(ClusterConfig.IndexMode.Enum.INDEX);
        }
    }

    // The semantics of visibility delay in search is deactivating caches if the
    // delay is less than 1.0, in qrs the cache is deactivated if the delay is 0
    // (or less). 1.0 seems a little arbitrary, so just doing the conversion
    // here instead of having two totally independent implementations having to
    // follow each other down in the modules.
    private static Double convertVisibilityDelay(Double visibilityDelay) {
        return (visibilityDelay < 1.0d) ? 0.0d : visibilityDelay;
    }

    @Override
    public String toString() { return "search-capable cluster '" + clusterName + "'"; }

    /**
     * Class used to retrieve combined configuration from multiple document databases.
     * It is not a direct {@link ConfigInstance.Producer} of those configs,
     * that is handled (by delegating to this) by the {@link IndexedSearchCluster}
     * which is the parent to this. This avoids building the config multiple times.
     */
    private record Join(Collection<DocumentDatabase> docDbs) {

        public void getConfig(IndexInfoConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public void getConfig(SchemaInfoConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public void getConfig(IlscriptsConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

        public void getConfig(AttributesConfig.Builder builder) {
            for (DocumentDatabase docDb : docDbs) {
                docDb.getConfig(builder);
            }
        }

    }

}
