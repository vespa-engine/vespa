// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.processing;

import com.yahoo.config.model.application.provider.BaseDeployLogger;
import com.yahoo.search.query.profile.QueryProfileRegistry;
import com.yahoo.schema.RankProfile.RankProperty;
import com.yahoo.schema.RankProfileRegistry;
import com.yahoo.schema.Schema;
import com.yahoo.schema.ApplicationBuilder;
import com.yahoo.schema.AbstractSchemaTestCase;
import com.yahoo.schema.parser.ParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.fail;

public class RankPropertyVariablesTestCase extends AbstractSchemaTestCase {

    @Test
    void testRankPropVariables() throws IOException, ParseException {
        RankProfileRegistry rankProfileRegistry = new RankProfileRegistry();
        Schema schema = ApplicationBuilder.buildFromFile("src/test/examples/rankpropvars.sd",
                new BaseDeployLogger(),
                rankProfileRegistry,
                new QueryProfileRegistry());
        assertRankPropEquals(rankProfileRegistry.get(schema, "other").getRankProperties(), "$testvar1", "foo");
        assertRankPropEquals(rankProfileRegistry.get(schema, "other").getRankProperties(), "$testvar_2", "bar");
        assertRankPropEquals(rankProfileRegistry.get(schema, "other").getRankProperties(), "$testvarOne23", "baz");
        assertRankPropEquals(rankProfileRegistry.get(schema, "another").getRankProperties(), "$Testvar1", "1");
        assertRankPropEquals(rankProfileRegistry.get(schema, "another").getRankProperties(), "$Testvar_4", "4");
        assertRankPropEquals(rankProfileRegistry.get(schema, "another").getRankProperties(), "$testvarFour23", "234234.234");
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
