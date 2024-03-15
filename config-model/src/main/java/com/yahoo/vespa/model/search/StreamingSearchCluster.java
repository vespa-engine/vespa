// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.config.search.SummaryConfig;
import com.yahoo.vespa.config.search.core.OnnxModelsConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;
import com.yahoo.vespa.config.search.core.RankingConstantsConfig;
import com.yahoo.vespa.config.search.core.RankingExpressionsConfig;
import com.yahoo.vespa.config.search.summary.JuniperrcConfig;
import com.yahoo.vespa.config.search.vsm.VsmfieldsConfig;
import com.yahoo.vespa.config.search.vsm.VsmsummaryConfig;

/**
 * A search cluster of type streaming.
 * 
 * @author baldersheim
 * @author vegardh
 */
public class StreamingSearchCluster extends SearchCluster implements
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
    private final String storageRouteSpec;
    private final AttributesProducer attributesConfig;
    private final String docTypeName;

    public StreamingSearchCluster(TreeConfigProducer<AnyConfigProducer> parent, String clusterName, int index,
                                  String docTypeName, String storageRouteSpec) {
        super(parent, clusterName, index);
        attributesConfig = new AttributesProducer(parent, docTypeName);
        this.docTypeName = docTypeName;
        this.storageRouteSpec = storageRouteSpec;
    }

    @Override
    protected IndexingMode getIndexingMode() { return IndexingMode.STREAMING; }
    public final String getStorageRouteSpec() { return storageRouteSpec; }

    public String getDocTypeName() { return docTypeName; }

    public DerivedConfiguration derived() { return db().getDerivedConfiguration(); }

    @Override
    public void deriveFromSchemas(DeployState deployState) {
        if (schemas().isEmpty()) return;
        if (schemas().size() > 1) throw new IllegalArgumentException("Only a single schema is supported, got " + schemas().size());

        Schema schema = schemas().values().stream().findAny().get().fullSchema();
        if ( ! schema.getName().equals(docTypeName))
            throw new IllegalArgumentException("Document type name '" + docTypeName +
                                               "' must be the same as the schema name '" + schema.getName() + "'");
        add(new DocumentDatabase(this, docTypeName, new DerivedConfiguration(deployState, schema, SchemaInfo.IndexMode.STREAMING)));
    }

    protected void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        super.fillDocumentDBConfig(sdoc, ddbB);
        ddbB.configid(attributesConfig.getConfigId()); // Temporary until fully cleaned up
    }

    private DocumentDatabase db() { return getDocumentDbs().get(0); }

    // These are temporary until backend uses correct config id.
    @Override public void getConfig(SummaryConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(OnnxModelsConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(RankingConstantsConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(RankProfilesConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(RankingExpressionsConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(JuniperrcConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(VsmfieldsConfig.Builder builder) { db().getConfig(builder); }
    @Override public void getConfig(VsmsummaryConfig.Builder builder) { db().getConfig(builder);}

    private class AttributesProducer extends AnyConfigProducer implements AttributesConfig.Producer {

        AttributesProducer(TreeConfigProducer<AnyConfigProducer> parent, String docType) {
            super(parent, docType);
        }

        @Override
        public void getConfig(AttributesConfig.Builder builder) {
            derived().getConfig(builder, AttributeFields.FieldSet.FAST_ACCESS);
        }
    }

}
