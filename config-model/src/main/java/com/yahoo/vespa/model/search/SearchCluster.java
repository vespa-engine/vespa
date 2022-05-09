// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.searchdefinition.RankProfile;
import com.yahoo.searchdefinition.derived.SchemaInfo;
import com.yahoo.searchdefinition.derived.SummaryMap;
import com.yahoo.searchlib.rankingexpression.Reference;
import com.yahoo.tensor.TensorType;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;
import com.yahoo.searchdefinition.derived.DerivedConfiguration;
import com.yahoo.config.model.producer.AbstractConfigProducer;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Represents a search cluster.
 *
 * @author arnej27959
 */
public abstract class SearchCluster extends AbstractConfigProducer<SearchCluster>
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

    public SearchCluster(AbstractConfigProducer<?> parent, String clusterName, int index) {
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

    /**
     * Converts summary and summary map config to the appropriate information in documentdb
     *
     * @param summaryConfigProducer the summary config
     * @param summarymapConfigProducer the summary map config, or null if none is available
     * @param docDb the target document dm config
     */
    protected void convertSummaryConfig(SummaryConfig.Producer summaryConfigProducer,
                                        SummarymapConfig.Producer summarymapConfigProducer,
                                        DocumentdbInfoConfig.Documentdb.Builder docDb) {

        SummaryConfig.Builder summaryConfigBuilder = new SummaryConfig.Builder();
        summaryConfigProducer.getConfig(summaryConfigBuilder);
        SummaryConfig summaryConfig = summaryConfigBuilder.build();

        SummarymapConfig summarymapConfig = null;
        if (summarymapConfigProducer != null) {
            SummarymapConfig.Builder summarymapConfigBuilder = new SummarymapConfig.Builder();
            summarymapConfigProducer.getConfig(summarymapConfigBuilder);
            summarymapConfig = summarymapConfigBuilder.build();
        }

        for (SummaryConfig.Classes sclass : summaryConfig.classes()) {
            DocumentdbInfoConfig.Documentdb.Summaryclass.Builder sumClassBuilder = new DocumentdbInfoConfig.Documentdb.Summaryclass.Builder();
            sumClassBuilder.
                id(sclass.id()).
                name(sclass.name());
            for (SummaryConfig.Classes.Fields field : sclass.fields()) {
                DocumentdbInfoConfig.Documentdb.Summaryclass.Fields.Builder fieldsBuilder = new DocumentdbInfoConfig.Documentdb.Summaryclass.Fields.Builder();
                fieldsBuilder.name(field.name())
                             .type(field.type())
                             .dynamic(isDynamic(field.name(), summarymapConfig));
                sumClassBuilder.fields(fieldsBuilder);
            }
            docDb.summaryclass(sumClassBuilder);
        }
    }

    /** Returns whether the given field is a dynamic summary field. */
    private boolean isDynamic(String fieldName, SummarymapConfig summarymapConfig) {
        if (summarymapConfig == null) return false; // not know for streaming, but also not used

        for (SummarymapConfig.Override override : summarymapConfig.override()) {
            if ( ! fieldName.equals(override.field())) continue;
            if (SummaryMap.isDynamicCommand(override.command())) return true;
        }
        return false;
    }

    protected void addRankProfilesConfig(String schemaName, DocumentdbInfoConfig.Documentdb.Builder docDbBuilder) {
        for (SchemaInfo.RankProfileInfo rankProfile : schemas().get(schemaName).rankProfiles().values()) {
            var rankProfileConfig = new DocumentdbInfoConfig.Documentdb.Rankprofile.Builder();
            rankProfileConfig.name(rankProfile.name());
            rankProfileConfig.hasSummaryFeatures(rankProfile.hasSummaryFeatures());
            rankProfileConfig.hasRankFeatures(rankProfile.hasRankFeatures());
            for (var input : rankProfile.inputs().entrySet()) {
                var inputConfig = new DocumentdbInfoConfig.Documentdb.Rankprofile.Input.Builder();
                inputConfig.name(input.getKey().toString());
                inputConfig.type(input.getValue().type().toString());
                rankProfileConfig.input(inputConfig);
            }
            docDbBuilder.rankprofile(rankProfileConfig);
        }
    }

    /** Returns a list of the document type names used in this search cluster */
    public List<String> getDocumentNames() {
        return schemas.values()
                      .stream()
                      .map(schema -> schema.fullSchema().getDocument().getDocumentName().getName())
                      .collect(Collectors.toList());
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
    public abstract DerivedConfiguration getSchemaConfig();

    // TODO: The get methods below should be moved to StreamingSearchCluster

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getIndexInfo().getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getSchemaInfo().getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getIndexingScript().getConfig(builder);
    }

    public void getConfig(AttributesConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getConfig(builder);
    }

    public void getConfig(RankProfilesConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getRankProfileList().getConfig(builder);
    }

    @Override
    public abstract void getConfig(DocumentdbInfoConfig.Builder builder);

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
