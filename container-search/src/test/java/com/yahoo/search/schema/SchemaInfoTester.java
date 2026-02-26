// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.schema;

import com.yahoo.container.QrSearchersConfig;
import com.yahoo.search.Query;
import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.search.schema.RankProfile.InputType;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class SchemaInfoTester {

    private final SchemaInfo schemaInfo;

    SchemaInfoTester() {
        this.schemaInfo = createSchemaInfo();
    }

    SchemaInfo schemaInfo() { return schemaInfo; }

    Query query(String sources, String restrict) {
        Map<String, String> params = new HashMap<>();
        if ( ! sources.isEmpty())
            params.put("sources", sources);
        if ( ! restrict.isEmpty())
            params.put("restrict", restrict);
        return new Query.Builder().setSchemaInfo(schemaInfo)
                                  .setRequestMap(params)
                                  .build();
    }

    void assertInput(TensorType expectedType, String sources, String restrict, String rankProfile, String feature) {
        assertEquals(expectedType,
                     schemaInfo.newSession(query(sources, restrict)).rankProfileInput(feature, rankProfile).tensorType());
    }

    void assertInputConflict(TensorType expectedType, String sources, String restrict, String rankProfile, String feature) {
        try {
            assertInput(expectedType, sources, restrict, rankProfile, feature);
        }
        catch (IllegalArgumentException e) {
            assertEquals("Conflicting input type declarations for '" + feature + "'",
                         e.getMessage().split(":")[0]);
        }
    }

    static SchemaInfo createSchemaInfo() {
        List<Schema> schemas = new ArrayList<>();
        RankProfile.Builder common = new RankProfile.Builder("commonProfile")
                .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                .addInput("query(myTensor2)", InputType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor3)", InputType.fromSpec("tensor(x[2],y[2])"))
                .addInput("query(myTensor4)", InputType.fromSpec("tensor<float>(x[5])"));
        schemas.add(new Schema.Builder("a")
                            .add(common.build())
                            .add(new Field.Builder("field1", "string").setIndex(true).addAlias("alias1").addAlias("alias2").build())
                            .add(new Field.Builder("field2", "int").setAttribute(true).build())
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .add(new RankProfile.Builder("withRerankCount")
                                         .setSecondPhase(new SecondPhase.Builder().setRerankCount(201).build())
                                         .build())
                            .add(new RankProfile.Builder("withTotalRerankCount")
                                         .setSecondPhase(new SecondPhase.Builder().setTotalRerankCount(2001).build())
                                         .build())
                            .add(new DocumentSummary.Builder("testSummary")
                                         .add(new DocumentSummary.Field("field1", "string"))
                                         .add(new DocumentSummary.Field("field2", "integer"))
                                         .setDynamic(true)
                                         .build())
                            .build());
        schemas.add(new Schema.Builder("b")
                            .add(common.build())
                            .add(new RankProfile.Builder("inconsistent")
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(x[10])"))
                                         .build())
                            .add(new RankProfile.Builder("bOnly")
                                         .setUseSignificanceModel(true)
                                         .addInput("query(myTensor1)", InputType.fromSpec("tensor(a{},b{})"))
                                         .build())
                            .build());
        List<Cluster> clusters = new ArrayList<>();
        clusters.add(new Cluster.Builder("ab").addSchema("a").addSchema("b").build());
        clusters.add(new Cluster.Builder("a").addSchema("a").setStreaming(true).build());
        return new SchemaInfo(schemas, clusters);
    }

    /** Creates the same schema info as createSchemaInfo from config objects. */
    static SchemaInfo createSchemaInfoFromConfig() {
        var rankProfileCommon = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileCommon.name("commonProfile");
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor2)").type("tensor(x[2],y[2])"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor3)").type("tensor(x[2],y[2])"));
        rankProfileCommon.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor4)").type("tensor<float>(x[5])"));

        var schemaInfoInfoConfig = new SchemaInfoConfig.Builder();

        // ----- Schema A
        var schemaA = new SchemaInfoConfig.Schema.Builder();
        schemaA.name("a");

        schemaA.field(new SchemaInfoConfig.Schema.Field.Builder().name("field1").type("string")
                                                                 .index(true).attribute(false).bitPacked(false)
                                                                 .alias("alias1").alias("alias2"));
        schemaA.field(new SchemaInfoConfig.Schema.Field.Builder().name("field2").type("int")
                                                                 .index(false).attribute(true).bitPacked(false));

        schemaA.rankprofile(rankProfileCommon);

        var rankProfileWithRerankCount = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileWithRerankCount.name("withRerankCount");
        rankProfileWithRerankCount.rerankCount(201);
        schemaA.rankprofile(rankProfileWithRerankCount);

        var rankProfileWithTotalRerankCount = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileWithTotalRerankCount.name("withTotalRerankCount");
        rankProfileWithTotalRerankCount.totalRerankCount(2001);
        schemaA.rankprofile(rankProfileWithTotalRerankCount);

        var rankProfileInconsistentA = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileInconsistentA.name("inconsistent");
        rankProfileInconsistentA.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        schemaA.rankprofile(rankProfileInconsistentA);

        var summaryClass = new SchemaInfoConfig.Schema.Summaryclass.Builder();
        summaryClass.name("testSummary");
        var field1 = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
        field1.name("field1").type("string").dynamic(true);
        summaryClass.fields(field1);
        var field2 = new SchemaInfoConfig.Schema.Summaryclass.Fields.Builder();
        field2.name("field2").type("integer");
        summaryClass.fields(field2);
        schemaA.summaryclass(summaryClass);

        schemaInfoInfoConfig.schema(schemaA);

        // ----- Schema B
        var schemaB = new SchemaInfoConfig.Schema.Builder();
        schemaB.name("b");

        schemaB.rankprofile(rankProfileCommon);
        var rankProfileInconsistentB = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileInconsistentB.name("inconsistent");
        rankProfileInconsistentB.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(x[10])"));
        schemaB.rankprofile(rankProfileInconsistentB);
        var rankProfileBOnly = new SchemaInfoConfig.Schema.Rankprofile.Builder();
        rankProfileBOnly.name("bOnly")
                .significance(new SchemaInfoConfig.Schema.Rankprofile.Significance.Builder().useModel(true));
        rankProfileBOnly.input(new SchemaInfoConfig.Schema.Rankprofile.Input.Builder().name("query(myTensor1)").type("tensor(a{},b{})"));
        schemaB.rankprofile(rankProfileBOnly);

        schemaInfoInfoConfig.schema(schemaB);

        // ----- Info about clusters
        var qrSearchersConfig = new QrSearchersConfig.Builder();
        var clusterAB = new QrSearchersConfig.Searchcluster.Builder();
        clusterAB.name("ab");
        clusterAB.searchdef("a").searchdef("b");
        qrSearchersConfig.searchcluster(clusterAB);
        var clusterA = new QrSearchersConfig.Searchcluster.Builder();
        clusterA.name("a");
        clusterA.indexingmode(QrSearchersConfig.Searchcluster.Indexingmode.Enum.STREAMING);
        clusterA.searchdef("a");
        qrSearchersConfig.searchcluster(clusterA);

        return new SchemaInfo(schemaInfoInfoConfig.build(), qrSearchersConfig.build());
    }

}
