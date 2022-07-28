// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.vespa.model.search.test;

import com.yahoo.search.config.SchemaInfoConfig;
import com.yahoo.vespa.config.search.RankProfilesConfig;
import com.yahoo.vespa.model.VespaModel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class SchemaInfoTestCase {

    /** Schema-info should contain all schemas, independent of clusters. */
    @Test
    void requireThatSchemaInfoIsAvailable() {
        List.of(1.0, 2.0, 3.0).toArray(new Double[3]);
        String inputs =
                "  rank-profile inputs {" +
                        "    inputs {" +
                        "      query(foo) tensor<float>(x[10])" +
                        "      query(bar) tensor(key{},x[1000])" +
                        "      query(myDouble1) double: 0.5" +
                        "      query(myDouble2) tensor()" +
                        "      query(myMap) tensor(key{}): { label1:1.0,\n \"label2\": 2.0, 'label3': 3.0 }" +
                        "      query(myVector1) tensor(x[3]):\n\n[1 ,2.0,3]" +
                        "      query(myVector2) tensor(x[3]):{{x:0}:1,{x: 1}: 2 , { x:2}:3.0 }" +
                        "      query(myMatrix) tensor(x[2],y[3]):[[1.0, 2.0, 3.0], [4.0, 5.0, 6.0]]" +
                        "      query(myMixed1) tensor(key{},x[2]): { key1:[-1.0, 1.1], key2: [1,2]}" +
                        "      query(myMixed2) tensor(k1{},k2{},x[2]): { {k1:l1,k2:l1}:[-1.0, 1.1], {k1:l1,k2:l2}: [1,2]}" +
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
            RankProfilesConfig rankProfilesConfig = model.getConfig(RankProfilesConfig.class, "test/search/cluster.test/type1");

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

                var schemaInfoProfile = tester.assertRankProfile(schema, 6, "inputs", false, false);
                assertEquals(10, schemaInfoProfile.input().size());
                var rankProfilesProfile = rankProfilesConfig.rankprofile().get(6);
                assertEquals("inputs", rankProfilesProfile.name());
                assertInput("query(foo)", "tensor<float>(x[10])", null, 0, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(bar)", "tensor(key{},x[1000])", null, 1, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myDouble1)", "tensor()", "0.5", 2, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myDouble2)", "tensor()", null, 3, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myMap)", "tensor(key{})", "tensor(key{}):{{key:label1}:1.0, {key:label2}:2.0, {key:label3}:3.0}", 4, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myVector1)", "tensor(x[3])", "tensor(x[3]):{{x:0}:1.0, {x:1}:2.0, {x:2}:3.0}", 5, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myVector2)", "tensor(x[3])", "tensor(x[3]):{{x:0}:1.0, {x:1}:2.0, {x:2}:3.0}", 6, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myMatrix)", "tensor(x[2],y[3])", "tensor(x[2],y[3]):{{x:0,y:0}:1.0, {x:0,y:1}:2.0, {x:0,y:2}:3.0, {x:1,y:0}:4.0, {x:1,y:1}:5.0, {x:1,y:2}:6.0}", 7, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myMixed1)", "tensor(key{},x[2])", "tensor(key{},x[2]):{{key:key1,x:0}:-1.0, {key:key1,x:1}:1.1, {key:key2,x:0}:1.0, {key:key2,x:1}:2.0}", 8, schemaInfoProfile, rankProfilesProfile);
                assertInput("query(myMixed2)", "tensor(k1{},k2{},x[2])", "tensor(k1{},k2{},x[2]):{{k1:l1,k2:l1,x:0}:-1.0, {k1:l1,k2:l1,x:1}:1.1, {k1:l1,k2:l2,x:0}:1.0, {k1:l1,k2:l2,x:1}:2.0}", 9, schemaInfoProfile, rankProfilesProfile);

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

    private void assertInput(String name, String type, String defaultValue,
                             int index,
                             SchemaInfoConfig.Schema.Rankprofile schemaInfoProfile,
                             RankProfilesConfig.Rankprofile rankProfilesProfile) {
        assertEquals(name, schemaInfoProfile.input(index).name());
        assertEquals(type, schemaInfoProfile.input(index).type());
        if (defaultValue != null) {
            boolean found = false;
            for (var property : rankProfilesProfile.fef().property()) {
                if (property.name().equals(name)) {
                    assertEquals(defaultValue, property.value());
                    found = true;
                }
            }
            if ( ! found)
                fail("Missing property " + name);
        }
    }

}
