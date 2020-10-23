// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition;

import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.derived.AttributeFields;
import com.yahoo.searchdefinition.derived.RawRankProfile;
import com.yahoo.searchdefinition.parser.ParseException;
import ai.vespa.rankingexpression.importer.configmodelview.ImportedMlModels;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * @author bratseth
 */
public class RankPropertiesTestCase extends SchemaTestCase {

    @Test
    public void testRankPropertyInheritance() throws ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        SearchBuilder builder = new SearchBuilder(rankProfileRegistry);
        builder.importString(
                "search test {\n" +
                        "    document test { \n" +
                        "        field a type string { \n" +
                        "            indexing: index \n" +
                        "        }\n" +
                        "    }\n" +
                        "    \n" +
                        "    rank-profile parent {\n" +
                        "        first-phase {\n" +
                        "            expression: a\n" +
                        "        }\n" +
                        "        rank-properties {\n" +
                        "            query(a): 1500 \n" +
                        "        }\n" +
                        "    }\n" +
                        "    rank-profile child inherits parent {\n" +
                        "        first-phase {\n" +
                        "            expression: a\n" +
                        "        }\n" +
                        "        rank-properties {\n" +
                        "            query(a): 2000 \n" +
                        "        }\n" +
                        "    }\n" +
                        "\n" +
                        "}\n");
        builder.build();
        Search search = builder.getSearch();
        AttributeFields attributeFields = new AttributeFields(search);

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(search, "parent");
            assertEquals("query(a) = 1500", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawParent = new RawRankProfile(parent, new QueryProfileRegistry(), new ImportedMlModels(), attributeFields);
            assertEquals("(query(a), 1500)", rawParent.configProperties().get(0).toString());
        }

        {
            // Check declared model
            RankProfile parent = rankProfileRegistry.get(search, "child");
            assertEquals("query(a) = 2000", parent.getRankProperties().get(0).toString());

            // Check derived model
            RawRankProfile rawChild = new RawRankProfile(rankProfileRegistry.get(search, "child"),
                                                         new QueryProfileRegistry(),
                                                         new ImportedMlModels(),
                                                         attributeFields);
            assertEquals("(query(a), 2000)", rawChild.configProperties().get(0).toString());
        }
    }

}
