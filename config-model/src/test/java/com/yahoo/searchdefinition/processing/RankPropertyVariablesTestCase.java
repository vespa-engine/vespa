// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.searchdefinition.RankProfile.RankProperty;
import com.yahoo.searchdefinition.RankProfileRegistry;
import com.yahoo.searchdefinition.Search;
import com.yahoo.searchdefinition.SearchBuilder;
import com.yahoo.searchdefinition.SearchDefinitionTestCase;
import com.yahoo.searchdefinition.parser.ParseException;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.Assert.fail;

public class RankPropertyVariablesTestCase extends SearchDefinitionTestCase {

    @Test
    public void testRankPropVariables() throws IOException, ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        Search search = SearchBuilder.buildFromFile("src/test/examples/rankpropvars.sd",
                                                    new BaseDeployLogger(),
                                                    rankProfileRegistry,
                                                    new QueryProfileRegistry());
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "other").getRankProperties(), "$testvar1", "foo");
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "other").getRankProperties(), "$testvar_2", "bar");
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "other").getRankProperties(), "$testvarOne23", "baz");
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "another").getRankProperties(), "$Testvar1", "1");
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "another").getRankProperties(), "$Testvar_4", "4");
        assertRankPropEquals(rankProfileRegistry.getRankProfile(search, "another").getRankProperties(), "$testvarFour23", "234234.234");
    }

    private void assertRankPropEquals(List<RankProperty> props, String key, String val) {
        for (RankProperty prop : props) {
            if (prop.getName().equals(key)) {
                if (prop.getValue().equals(val)) {
                    return;
                }
            }
        }
        fail(key+":"+val+ " not found in rank properties.");
    }

}
