// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.ConfigInstance;
import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
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
    private final List<DocumentDatabase> documentDbs = new LinkedList<>();

    public SearchCluster(TreeConfigProducer<?> parent, String clusterName, int index) {
        super(parent, "cluster." + clusterName);
        this.clusterName = clusterName;
        this.index = index;
    }

    public void add(SchemaInfo schema) {
        schemas.put(schema.name(), schema);
    }
    public void add(DocumentDatabase db) {
        documentDbs.add(db);
    }

    public boolean hasDocumentDB(String name) {
        for (DocumentDatabase db : documentDbs) {
            if (db.getName().equals(name)) {
                return true;
            }
        }
        return false;
    }

    public String getConfigId(String name) {
        for (DocumentDatabase db : documentDbs) {
            if (db.getName().equals(name)) {
                return db.getConfigId();
            }
        }
        return "";
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
        return Collections.unmodifiableList(documentDbs);
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
        for (DocumentDatabase sdoc : documentDbs) {
            if (sdoc.getName().equals(documentType)) {
                fillDocumentDBConfig(sdoc, builder);
                return;
            }
        }
    }

    protected void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        ddbB.inputdoctypename(sdoc.getSchemaName())
            .configid(sdoc.getConfigId());
    }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        for (DocumentDatabase db : documentDbs) {
            DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
            docDb.name(db.getName());
            builder.documentdb(docDb);
        }
    }
    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        new Join(documentDbs).getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        new Join(documentDbs).getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        new Join(documentDbs).getConfig(builder);
    }

    public void getConfig(AttributesConfig.Builder builder) {
        new Join(documentDbs).getConfig(builder);
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
    private record Join(List<DocumentDatabase> docDbs) {

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
