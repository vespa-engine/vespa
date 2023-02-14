// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents a search cluster.
 *
 * @author arnej27959
 */
public abstract class SearchCluster extends TreeConfigProducer<AnyConfigProducer>
        implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer,
        SchemaInfoConfig.Producer {

    private final String clusterName;
    private int index;
    private Double queryTimeout;
    private Double visibilityDelay = 0.0;
    private final Map<String, SchemaInfo> schemas = new LinkedHashMap<>();

    public SearchCluster(TreeConfigProducer<?> parent, String clusterName, int index) {
        super(parent, "cluster." + clusterName);
        this.clusterName = clusterName;
        this.index = index;
    }

    public void add(SchemaInfo schema) {
        schemas.put(schema.name(), schema);
    }

    /** Returns the schemas that should be active in this cluster. Note: These are added during processing. */
    public Map<String, SchemaInfo> schemas() { return Collections.unmodifiableMap(schemas); }

    /**
     * Must be called after cluster is built, to derive schema configs.
     * Derives the schemas from the application package.
     * Also stores the document names contained in the schemas.
     */
    public abstract void deriveFromSchemas(DeployState deployState);

    /** Returns a list of the document type names used in this search cluster */
    public List<String> getDocumentNames() {
        return schemas.values()
                      .stream()
                      .map(schema -> schema.fullSchema().getDocument().getDocumentName().getName())
                      .toList();
    }

    public String getClusterName()              { return clusterName; }
    public final String getIndexingModeName()   { return getIndexingMode().getName(); }
    public final boolean isStreaming()          { return getIndexingMode() == IndexingMode.STREAMING; }

    public final void setQueryTimeout(Double to) { this.queryTimeout = to; }
    public final void setVisibilityDelay(double delay) { this.visibilityDelay = delay; }

    protected abstract IndexingMode getIndexingMode();
    public final Double getVisibilityDelay() { return visibilityDelay; }
    public final Double getQueryTimeout() { return queryTimeout; }
    public abstract int getRowBits();
    public final void setClusterIndex(int index) { this.index = index; }
    public final int getClusterIndex() { return index; }

    public abstract void defaultDocumentsConfig();

    public abstract void getConfig(AttributesConfig.Builder builder);

    public abstract void getConfig(RankProfilesConfig.Builder builder);

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

}
