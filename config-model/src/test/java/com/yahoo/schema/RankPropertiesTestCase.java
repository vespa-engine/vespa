// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.derived.AttributeFields;
import com.yahoo.schema.derived.RawRankProfile;
import com.yahoo.schema.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * @author bratseth
 */
public class RankPropertiesTestCase extends AbstractSchemaTestCase {

    @Test
    void testRankPropertyInheritance() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(joinLines(
                "search test {",
                "    document test {",
                "        field a type string { ",
                "            indexing: index ",
                "        }",
                "    }",
                "    rank-profile parent {",
                "        first-phase {",
                "            expression: a",
                "        }",
                "        rank-properties {",
                "            query(a): 1500 ",
                "        }",
                "    }",
                "    rank-profile child inherits parent {",
                "        first-phase {",
                "            expression: a",
                "        }",
                "        rank-properties {",
                "            query(a): 2000 ",
                "        }",
                "    }",
                "}"));
        builder.build(true);
        Schema schema = builder.getSchema();
        AttributeFields attributeFields = new AttributeFields(schema);

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(schema, "parent");
            assertEquals("query(a) = 1500", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawParent = new RawRankProfile(parent, new LargeRankingExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), attributeFields, new TestProperties());
            assertEquals("(query(a), 1500)", rawParent.configProperties().get(0).toString());
        }

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(schema, "child");
            assertEquals("query(a) = 2000", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawChild = new RawRankProfile(rankProfileRegistry.get(schema, "child"),
                    new LargeRankingExpressions(new MockFileRegistry()),
                    new QueryProfileRegistry(),
                    new ImportedMlModels(),
                    attributeFields,
                    new TestProperties());
            assertEquals("(query(a), 2000)", rawChild.configProperties().get(0).toString());
        }
    }

    @Test
    public void testDefaultRankProperties() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry, new QueryProfileRegistry());
        builder.addSchema(joinLines(
                "search test {",
                "    document test {",
                "        field a type string { ",
                "            indexing: index ",
                "        }",
                "    }",
                "    rank-profile a {",
                "        first-phase {",
                "            expression: a",
                "        }",
                "    }",
                "    rank-profile b {",
                "        first-phase {",
                "            expression: a",
                "        }",
                "        rank-properties {",
                "            query(a): 2000 ",
                "        }",
                "    }",
                "}"));
        builder.build(true);
        Schema schema = builder.getSchema();
        List<RankProfile.RankProperty> props = rankProfileRegistry.get(schema, "a").getRankProperties();
        assertTrue(props.isEmpty());

        props = rankProfileRegistry.get(schema, "b").getRankProperties();
        assertEquals(1, props.size());
        assertEquals(new RankProfile.RankProperty("query(a)","2000"), props.get(0));
    }

    @Test
    void testRankProfileMutate() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        ApplicationBuilder builder = new ApplicationBuilder(rankProfileRegistry);
        builder.addSchema(joinLines(
                "search test {",
                "    document test {",
                "        field a type int { ",
                "            indexing: attribute ",
                "        }",
                "    }",
                "    field synthetic_attribute_a type int {",
                "        indexing: attribute",
                "        attribute: mutable",
                "    }",
                "    field synthetic_attribute_b type double {",
                "        indexing: attribute",
                "        attribute: mutable",
                "    }",
                "    field synthetic_attribute_c type long {",
                "        indexing: attribute",
                "        attribute: mutable",
                "    }",
                "    rank-profile a {",
                "        mutate {",
                "          on-match {",
                "            synthetic_attribute_a += 7",
                "          }",
                "          on-first-phase {",
                "            synthetic_attribute_b +=1",
                "          }",
                "          on-second-phase {",
                "            synthetic_attribute_b = 1.01",
                "          }",
                "          on-summary {",
                "            synthetic_attribute_c -= 1",
                "          }",
                "        }",
                "        first-phase {",
                "            expression: a",
                "        }",
                "    }",
                "    rank-profile b {",
                "        first-phase {",
                "            expression: a",
                "        }",
                "        second-phase {",
                "            expression: a",
                "        }",
                "    }",
                "}"));
        builder.build(true);
        Schema schema = builder.getSchema();
        RankProfile a = rankProfileRegistry.get(schema, "a");
        List<RankProfile.MutateOperation> operations = a.getMutateOperations();
        assertEquals(4, operations.size());
        assertEquals(RankProfile.MutateOperation.Phase.on_match, operations.get(0).phase);
        assertEquals("synthetic_attribute_a", operations.get(0).attribute);
        assertEquals("+=7", operations.get(0).operation);
        assertEquals(RankProfile.MutateOperation.Phase.on_first_phase, operations.get(1).phase);
        assertEquals("synthetic_attribute_b", operations.get(1).attribute);
        assertEquals("+=1", operations.get(1).operation);
        assertEquals(RankProfile.MutateOperation.Phase.on_second_phase, operations.get(2).phase);
        assertEquals("synthetic_attribute_b", operations.get(2).attribute);
        assertEquals("=1.01", operations.get(2).operation);
        assertEquals(RankProfile.MutateOperation.Phase.on_summary, operations.get(3).phase);
        assertEquals("synthetic_attribute_c", operations.get(3).attribute);
        assertEquals("-=1", operations.get(3).operation);

        AttributeFields attributeFields = new AttributeFields(schema);
        RawRankProfile raw = new RawRankProfile(a, new LargeRankingExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), attributeFields, new TestProperties());
        assertEquals(9, raw.configProperties().size());
        assertEquals("(vespa.mutate.on_match.attribute, synthetic_attribute_a)", raw.configProperties().get(0).toString());
        assertEquals("(vespa.mutate.on_match.operation, +=7)", raw.configProperties().get(1).toString());
        assertEquals("(vespa.mutate.on_first_phase.attribute, synthetic_attribute_b)", raw.configProperties().get(2).toString());
        assertEquals("(vespa.mutate.on_first_phase.operation, +=1)", raw.configProperties().get(3).toString());
        assertEquals("(vespa.mutate.on_second_phase.attribute, synthetic_attribute_b)", raw.configProperties().get(4).toString());
        assertEquals("(vespa.mutate.on_second_phase.operation, =1.01)", raw.configProperties().get(5).toString());
        assertEquals("(vespa.mutate.on_summary.attribute, synthetic_attribute_c)", raw.configProperties().get(6).toString());
        assertEquals("(vespa.mutate.on_summary.operation, -=1)", raw.configProperties().get(7).toString());
        assertEquals("(vespa.rank.firstphase, a)", raw.configProperties().get(8).toString());
    }

}
