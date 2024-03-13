// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.deploy.DeployState;
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
    private final List<LegacyStreamingproxy> legacyproxy = new ArrayList<>();

    private static class LegacyStreamingproxy extends TreeConfigProducer<AnyConfigProducer> implements
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
        LegacyStreamingproxy(TreeConfigProducer<AnyConfigProducer> parent, String clusterName, DocumentDatabase db) {
            super(parent, "cluster." + clusterName + "." + db.getName());
            this.db = new DocumentDatabase(this, db.getName(), db.getDerivedConfiguration());
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

    public SearchCluster(TreeConfigProducer<?> parent, String clusterName, int index) {
        super(parent, "cluster." + clusterName);
        this.clusterName = clusterName;
        this.index = index;
    }

    public String getStorageRouteSpec() { return getClusterName(); }

    public void add(SchemaInfo schema) {
        schemas.put(schema.name(), schema);
    }
    public void add(DocumentDatabase db) {
        if (db.getDerivedConfiguration().isStreaming()) {
            legacyproxy.add(new LegacyStreamingproxy((TreeConfigProducer<AnyConfigProducer>) getParent(), clusterName, db));
        }
        documentDbs.put(db.getName(), db);
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
    public abstract void deriveFromSchemas(DeployState deployState);

    /** Returns the document databases contained in this cluster */
    public List<DocumentDatabase> getDocumentDbs() {
        return documentDbs.values().stream().toList();
    }

    public String getClusterName()              { return clusterName; }
    public final String getIndexingModeName()   { return getIndexingMode().getName(); }
    public final boolean isStreaming()          { return getIndexingMode() == IndexingMode.STREAMING; }

    public final void setQueryTimeout(Double to) { this.queryTimeout = to; }
    public final void setVisibilityDelay(double delay) { this.visibilityDelay = delay; }

    protected abstract IndexingMode getIndexingMode();
    public final Double getVisibilityDelay() { return visibilityDelay; }
    public final Double getQueryTimeout() { return queryTimeout; }
    public final void setClusterIndex(int index) { this.index = index; }
    public final int getClusterIndex() { return index; }

    public void fillDocumentDBConfig(String documentType, ProtonConfig.Documentdb.Builder builder) {
        DocumentDatabase db = documentDbs.get(documentType);
        if (db != null) {
            fillDocumentDBConfig(db, builder);
        }
    }

    protected void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        ddbB.inputdoctypename(sdoc.getSchemaName())
            .configid(sdoc.getConfigId());
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

    @Override
    public String toString() { return "search-capable cluster '" + clusterName + "'"; }

    public static final class IndexingMode {

        public static final IndexingMode REALTIME  = new IndexingMode("REALTIME");
        public static final IndexingMode STREAMING = new IndexingMode("STREAMING");

        private final String name;

        private IndexingMode(String name) {
            this.name = name;
        }

        public String getName() { return name; }

        public String toString() {
            return "indexingmode: " + name;
        }
    }

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
