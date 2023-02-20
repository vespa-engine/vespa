// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.prelude.fastsearch.DocumentdbInfoConfig;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.search.config.IndexInfoConfig;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;
import com.yahoo.vespa.configdefinition.IlscriptsConfig;

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
        SummaryConfig.Producer {

    private final String storageRouteSpec;
    private final AttributesProducer attributesConfig;
    private final String docTypeName;
    private DerivedConfiguration derivedConfig = null;

    public StreamingSearchCluster(TreeConfigProducer<AnyConfigProducer> parent,
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

    public String getDocTypeName() { return docTypeName; }

    public DerivedConfiguration derived() { return derivedConfig; }

    @Override
    public int getRowBits() { return 0; }

    @Override
    public void getConfig(DocumentdbInfoConfig.Builder builder) {
        DocumentdbInfoConfig.Documentdb.Builder docDb = new DocumentdbInfoConfig.Documentdb.Builder();
        docDb.name(derivedConfig.getSchema().getName());
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
        this.derivedConfig = new DerivedConfiguration(schema, deployState);
    }

    @Override
    public void defaultDocumentsConfig() { }

    @Override
    public void getConfig(IndexInfoConfig.Builder builder) {
        derivedConfig.getIndexInfo().getConfig(builder);
    }

    @Override
    public void getConfig(SchemaInfoConfig.Builder builder) {
        derivedConfig.getSchemaInfo().getConfig(builder);
    }

    @Override
    public void getConfig(IlscriptsConfig.Builder builder) {
        derivedConfig.getIndexingScript().getConfig(builder);
    }

    public void getConfig(AttributesConfig.Builder builder) {
        derivedConfig.getConfig(builder);
    }

    @Override
    public void getConfig(RankProfilesConfig.Builder builder) {
        derivedConfig.getRankProfileList().getConfig(builder);
    }

    @Override
    public void getConfig(VsmsummaryConfig.Builder builder) {
        if (derivedConfig.getVsmSummary() != null)
            derivedConfig.getVsmSummary().getConfig(builder);
    }
    
    @Override
    public void getConfig(VsmfieldsConfig.Builder builder) {
        if (derivedConfig.getVsmFields() != null)
            derivedConfig.getVsmFields().getConfig(builder);
    }
    
    @Override
    public void getConfig(SummaryConfig.Builder builder) {
        if (derivedConfig.getSummaries() != null)
            derivedConfig.getSummaries().getConfig(builder);
    }

    private class AttributesProducer extends AnyConfigProducer implements AttributesConfig.Producer {

        AttributesProducer(TreeConfigProducer<AnyConfigProducer> parent, String docType) {
            super(parent, docType);
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            derivedConfig.getConfig(builder, AttributeFields.FieldSet.FAST_ACCESS);
        }
    }

}
