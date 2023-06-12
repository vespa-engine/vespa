package com.yahoo.vespa.model.search.test;

import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.document.DataType;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.derived.SchemaInfo;
import com.yahoo.schema.derived.Summaries;
import com.yahoo.schema.document.SDDocumentType;
import com.yahoo.schema.document.SDField;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.documentmodel.DocumentSummary;
import com.yahoo.vespa.model.test.utils.DeployLoggerStub;

/**
 * @author bratseth
 */
public class SchemaInfoTester {

    public Schema createSchema(String name) {
        var schema = new Schema(name, null);
        var document = new SDDocumentType(name);
        schema.addDocument(document);
        schema.addSummary(new DocumentSummary("default", schema));
        return schema;
    }

    public String schemaInfoConfig(Schema schema) {
        var schemaInfo = new SchemaInfo(schema, new RankProfileRegistry(), new Summaries(schema, new DeployLoggerStub(), new TestProperties()));
        var schemaInfoConfigBuilder = new SchemaInfoConfig.Builder();
        schemaInfo.getConfig(schemaInfoConfigBuilder);
        var schemaInfoConfig = schemaInfoConfigBuilder.build();
        return schemaInfoConfig.toString();
    }

}
