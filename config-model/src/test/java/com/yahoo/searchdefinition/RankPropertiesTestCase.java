// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.config.model.application.provider.MockFileRegistry;
import com.yahoo.config.model.deploy.TestProperties;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.Test;

import java.util.List;

import static com.yahoo.config.model.test.TestUtil.joinLines;
import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RankPropertiesTestCase extends SchemaTestCase {

    @Test
    public void testRankPropertyInheritance() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(joinLines(
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
        builder.build();
        Search search = builder.getSearch();
        AttributeFields attributeFields = new AttributeFields(search);

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(search, "parent");
            assertEquals("query(a) = 1500", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawParent = new RawRankProfile(parent, new LargeRankExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), attributeFields, new TestProperties());
            assertEquals("(query(a), 1500)", rawParent.configProperties().get(0).toString());
        }

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(search, "child");
            assertEquals("query(a) = 2000", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawChild = new RawRankProfile(rankProfileRegistry.get(search, "child"),
                                                         new LargeRankExpressions(new MockFileRegistry()),
                                                         new QueryProfileRegistry(),
                                                         new ImportedMlModels(),
                                                         attributeFields,
                                                         new TestProperties());
            assertEquals("(query(a), 2000)", rawChild.configProperties().get(0).toString());
        }
    }
    @Test
    public void testRankProfileExecute() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(joinLines(
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
                "        execute {",
                "          on-match {",
                "            synthetic_attribute_a ++",
                "          }",
                "          on-rerank {",
                "            synthetic_attribute_b = 1.01",
                "          }",
                "          on-summary {",
                "            synthetic_attribute_c --",
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
        builder.build();
        Search search = builder.getSearch();
        RankProfile a = rankProfileRegistry.get(search, "a");
        List<RankProfile.ExecuteOperation> operations = a.getExecuteOperations();
        assertEquals(3, operations.size());
        assertEquals(RankProfile.ExecuteOperation.Phase.onmatch, operations.get(0).phase);
        assertEquals("synthetic_attribute_a", operations.get(0).attribute);
        assertEquals("++", operations.get(0).operation);
        assertEquals(RankProfile.ExecuteOperation.Phase.onrerank, operations.get(1).phase);
        assertEquals("synthetic_attribute_b", operations.get(1).attribute);
        assertEquals("=1.01", operations.get(1).operation);
        assertEquals(RankProfile.ExecuteOperation.Phase.onsummary, operations.get(2).phase);
        assertEquals("synthetic_attribute_c", operations.get(2).attribute);
        assertEquals("--", operations.get(2).operation);

        AttributeFields attributeFields = new AttributeFields(search);
        RawRankProfile raw = new RawRankProfile(a, new LargeRankExpressions(new MockFileRegistry()), new QueryProfileRegistry(), new ImportedMlModels(), attributeFields, new TestProperties());
        assertEquals(7, raw.configProperties().size());
        assertEquals("(vespa.execute.onmatch.attribute, synthetic_attribute_a)", raw.configProperties().get(0).toString());
        assertEquals("(vespa.execute.onmatch.operation, ++)", raw.configProperties().get(1).toString());
        assertEquals("(vespa.execute.onrerank.attribute, synthetic_attribute_b)", raw.configProperties().get(2).toString());
        assertEquals("(vespa.execute.onrerank.operation, =1.01)", raw.configProperties().get(3).toString());
        assertEquals("(vespa.execute.onsummary.attribute, synthetic_attribute_c)", raw.configProperties().get(4).toString());
        assertEquals("(vespa.execute.onsummary.operation, --)", raw.configProperties().get(5).toString());
        assertEquals("(vespa.rank.firstphase, a)", raw.configProperties().get(6).toString());
    }

}
