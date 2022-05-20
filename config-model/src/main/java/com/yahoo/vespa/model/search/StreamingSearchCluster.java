// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AbstractConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.SummarymapConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;

/**
 * A search cluster of type streaming.
 * 
 * @author baldersheim
 * @author vegardh
 */
public class StreamingSearchCluster extends SearchCluster implements 
        DocumentdbInfoConfig.Producer, 
        RankProfilesConfig.Producer,
        VsmsummaryConfig.Producer,
        VsmfieldsConfig.Producer,
        SummarymapConfig.Producer,
        SummaryConfig.Producer {

    private final String storageRouteSpec;
    private final AttributesProducer attributesConfig;
    private final String docTypeName;
    private DerivedConfiguration schemaConfig = null;

    public StreamingSearchCluster(AbstractConfigProducer<SearchCluster> parent,
                                  String clusterName,
                                  int index,
                                  String docTypeName,
                                  String storageRouteSpec) {
        super(parent, clusterName, index);
        attributesConfig = new AttributesProducer(parent, docTypeName);
        this.docTypeName = docTypeName;
        this.storageRouteSpec = storageRouteSpec;
    }

    public final String getDocumentDBConfigId() {
        return attributesConfig.getConfigId();
    }
    @Override
    protected IndexingMode getIndexingMode() { return IndexingMode.STREAMING; }
    public final String getStorageRouteSpec()       { return storageRouteSpec; }

    public String getDocTypeName() {
        return docTypeName;
    }

    @Override
    public int getRowBits() { return 0; }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
        docDb.name(schemaConfig.getSchema().getName());
        SummaryConfig.Producer prod = schemaConfig.getSummaries();
        convertSummaryConfig(prod, null, docDb);
        addRankProfilesConfig(schemaConfig.getSchema().getName(), docDb);
        builder.documentdb(docDb);
    }

    @Override
    public void deriveFromSchemas(DeployState deployState) {
        if (schemas().isEmpty()) return;
        if (schemas().size() > 1) throw new IllegalArgumentException("Only a single schema is supported, got " + schemas().size());

        Schema schema = schemas().values().stream().findAny().get().fullSchema();
        if ( ! schema.getName().equals(docTypeName))
            throw new IllegalArgumentException("Document type name '" + docTypeName +
                                               "' must be the same as the schema name '" + schema.getName() + "'");
        this.schemaConfig = new DerivedConfiguration(schema, deployState);
    }

    @Override
    public DerivedConfiguration getSchemaConfig() { return schemaConfig; }

    @Override
    public void defaultDocumentsConfig() { }

    @Override
    public void getConfig(AttributesConfig.Builder builder) {
        if (getSchemaConfig() != null) getSchemaConfig().getConfig(builder);
    }
    
    @Override
    public void getConfig(VsmsummaryConfig.Builder builder) {
        if (getSchemaConfig() != null)
            if (getSchemaConfig().getVsmSummary() != null)
                getSchemaConfig().getVsmSummary().getConfig(builder);
    }
    
    @Override
    public void getConfig(VsmfieldsConfig.Builder builder) {
        if (getSchemaConfig() != null)
            if (getSchemaConfig().getVsmFields() != null)
                getSchemaConfig().getVsmFields().getConfig(builder);
    }
    
    @Override
    public void getConfig(SummarymapConfig.Builder builder) {
        if (getSchemaConfig() != null)
            if (getSchemaConfig().getSummaryMap() != null)
                getSchemaConfig().getSummaryMap().getConfig(builder);
    }

    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        if (getSchemaConfig() != null)
            if (getSchemaConfig().getSummaries() != null)
                getSchemaConfig().getSummaries().getConfig(builder);
    }

    private class AttributesProducer extends AbstractConfigProducer<AttributesProducer> implements AttributesConfig.Producer {

        AttributesProducer(AbstractConfigProducer<?> parent, String docType) {
            super(parent, docType);
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            if (getSchemaConfig() != null) {
                getSchemaConfig().getConfig(builder, AttributeFields.FieldSet.FAST_ACCESS);
            }
        }
    }

}
