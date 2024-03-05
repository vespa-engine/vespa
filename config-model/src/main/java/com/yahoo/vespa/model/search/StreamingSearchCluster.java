// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search;

import com.yahoo.config.model.deploy.DeployState;
import com.yahoo.config.model.producer.AnyConfigProducer;
import com.yahoo.config.model.producer.TreeConfigProducer;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.DerivedConfiguration;
import com.yahoo.vespa.config.search.AttributesConfig;
import com.yahoo.vespa.config.search.core.ProtonConfig;

/**
 * A search cluster of type streaming.
 * 
 * @author baldersheim
 * @author vegardh
 */
public class StreamingSearchCluster extends SearchCluster
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

    public DerivedConfiguration derived() { return getDocumentDbs().get(0).getDerivedConfiguration(); }

    @Override
    public void deriveFromSchemas(DeployState deployState) {
        if (schemas().isEmpty()) return;
        if (schemas().size() > 1) throw new IllegalArgumentException("Only a single schema is supported, got " + schemas().size());

        Schema schema = schemas().values().stream().findAny().get().fullSchema();
        if ( ! schema.getName().equals(docTypeName))
            throw new IllegalArgumentException("Document type name '" + docTypeName +
                                               "' must be the same as the schema name '" + schema.getName() + "'");
        add(new DocumentDatabase(this, docTypeName, new DerivedConfiguration(schema, deployState, true)));
    }

    protected void fillDocumentDBConfig(DocumentDatabase sdoc, ProtonConfig.Documentdb.Builder ddbB) {
        super.fillDocumentDBConfig(sdoc, ddbB);
        ddbB.configid(attributesConfig.getConfigId()); // Temporary until fully cleaned up
    }

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
