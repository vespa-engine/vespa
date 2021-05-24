// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.config.model.producer.UserConfigRepo;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Superclass for search clusters.
 *
 * @author Peter Boros
 */
public abstract class AbstractSearchCluster extends AbstractConfigProducer<AbstractSearchCluster>
    implements
        DocumentdbInfoConfig.Producer,
        IndexInfoConfig.Producer,
        IlscriptsConfig.Producer {

    private Double queryTimeout;
    protected String clusterName;
    protected int index;
    private Double visibilityDelay = 0.0;
    private final List<String> documentNames = new ArrayList<>();
    private final List<SchemaSpec> localSDS = new LinkedList<>();

    public AbstractSearchCluster(AbstractConfigProducer<?> parent, String clusterName, int index) {
        super(parent, "cluster." + clusterName);
        this.clusterName = clusterName;
        this.index = index;
    }

    public void prepareToDistributeFiles(List<SearchNode> backends) {
        for (SchemaSpec sds : localSDS) {
            sds.getSearchDefinition().getSearch().rankingConstants().sendTo(backends);
            sds.getSearchDefinition().getSearch().onnxModels().sendTo(backends);
        }
    }

    public void addDocumentNames(NamedSchema searchDefinition) {
        String dName = searchDefinition.getSearch().getDocument().getDocumentName().getName();
        documentNames.add(dName);
    }

    /** Returns a List with document names used in this search cluster */
    public List<String> getDocumentNames() { return documentNames; }

    public List<SchemaSpec> getLocalSDS() {
        return localSDS;
    }

    public String getClusterName()              { return clusterName; }
    public final String getIndexingModeName()   { return getIndexingMode().getName(); }
    public final boolean isRealtime()           { return getIndexingMode() == IndexingMode.REALTIME; }
    public final boolean isStreaming()          { return getIndexingMode() == IndexingMode.STREAMING; }

    public final AbstractSearchCluster setQueryTimeout(Double to) {
        this.queryTimeout=to;
        return this;
    }

    public final AbstractSearchCluster setVisibilityDelay(double delay) {
        this.visibilityDelay=delay;
        return this;
    }

    protected abstract IndexingMode getIndexingMode();
    public final Double getVisibilityDelay() { return visibilityDelay; }
    public final Double getQueryTimeout() { return queryTimeout; }
    public abstract int getRowBits();
    public final void setClusterIndex(int index) { this.index = index; }
    public final int getClusterIndex() { return index; }

    @Override
    public abstract void getConfig(DocumentdbInfoConfig.Builder builder);
    @Override
    public abstract void getConfig(IndexInfoConfig.Builder builder);
    @Override
    public abstract void getConfig(IlscriptsConfig.Builder builder);
    public abstract void getConfig(RankProfilesConfig.Builder builder);
    public abstract void getConfig(AttributesConfig.Builder builder);

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

    public static final class SchemaSpec {

        private final NamedSchema searchDefinition;
        private final UserConfigRepo userConfigRepo;

        public SchemaSpec(NamedSchema searchDefinition, UserConfigRepo userConfigRepo) {
            this.searchDefinition = searchDefinition;
            this.userConfigRepo = userConfigRepo;
        }

        public NamedSchema getSearchDefinition() {
            return searchDefinition;
        }

        public UserConfigRepo getUserConfigs() {
            return userConfigRepo;
        }
    }

}
