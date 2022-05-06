// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

public class SchemaInfoTestCase {

    /** Schema-info should contain all schemas, independent of clusters. */
    @Test
    public void requireThatSchemaInfoIsAvailable() {
        String inputs =
                "  rank-profile inputs {" +
                "    inputs {" +
                "      query(foo) tensor<float>(x[10])" +
                "      query(bar) tensor(key{},x[1000])" +
                "      query(myDouble1) double" +
                "      query(myDouble2) tensor()" +
                "      query(myMap) tensor(key{}): { label1:1.0,\n \"label2\": 2.0, 'label3': 3.0 }" +
                "      query(myVector) tensor(x[3]):\n\n[1 ,2.0,3]" +
 //               "      query(myMixed) tensor(key{},x[2]): { key1:[-1.0, 1.1], key2: [1,2]}" +
                "    }" +
                "  }";
        List<String> schemas = List.of("type1", "type2");
        var tester = new SchemaTester();
        var model = tester.createModelWithRankProfile(inputs, schemas);
        assertSchemaInfo("container/searchchains/chain/test/component/com.yahoo.prelude.cluster.ClusterSearcher", model, tester);
        assertSchemaInfo("container", model, tester);
    }

    private void assertSchemaInfo(String configId, VespaModel model, SchemaTester tester) {
        {
            SchemaInfoConfig schemaInfoConfig = model.getConfig(SchemaInfoConfig.class, configId);
            assertEquals(2, schemaInfoConfig.schema().size());

            { // type1
                SchemaInfoConfig.Schema schema = schemaInfoConfig.schema(0);
                assertEquals("type1", schema.name());

                assertEquals(7, schema.rankprofile().size());
                tester.assertRankProfile(schema, 0, "default", false, false);
                tester.assertRankProfile(schema, 1, "unranked", false, false);
                tester.assertRankProfile(schema, 2, "staticrank", false, false);
                tester.assertRankProfile(schema, 3, "summaryfeatures", true, false);
                tester.assertRankProfile(schema, 4, "inheritedsummaryfeatures", true, false);
                tester.assertRankProfile(schema, 5, "rankfeatures", false, true);
                var inputs = tester.assertRankProfile(schema, 6, "inputs", false, false);

                assertEquals(6, inputs.input().size());
                assertInput("query(foo)", "tensor<float>(x[10])", inputs.input(0));
                assertInput("query(bar)", "tensor(key{},x[1000])", inputs.input(1));
                assertInput("query(myDouble1)", "tensor()", inputs.input(2));
                assertInput("query(myDouble2)", "tensor()", inputs.input(3));
                assertInput("query(myMap)", "tensor(key{})", inputs.input(4));
                assertInput("query(myVector)", "tensor(x[3])", inputs.input(5));
 //               assertInput("query(myMixed)", "tensor(key{},x[2])", inputs.input(5));

                assertEquals(2, schema.summaryclass().size());
                assertEquals("default", schema.summaryclass(0).name());
                assertEquals("attributeprefetch", schema.summaryclass(1).name());
                tester.assertSummaryField(schema, 0, 0, "f1", "longstring", true);
                tester.assertSummaryField(schema, 0, 1, "f2", "integer", false);
            }
            { // type2
                SchemaInfoConfig.Schema schema = schemaInfoConfig.schema(1);
                assertEquals("type2", schema.name());
            }
        }
    }

    private void assertInput(String name, String type, SchemaInfoConfig.Schema.Rankprofile.Input input) {
        assertEquals(name, input.name());
        assertEquals(type, input.type());
    }

}
