// Copyright 2019 Oath Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.searchdefinition.derived;

import com.yahoo.component.ComponentId;
import com.yahoo.prelude.query.QueryException;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.search.query.profile.config.QueryProfileConfigurer;
import com.yahoo.searchdefinition.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.Test;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class NearestNeighborTestCase extends AbstractExportingTestCase {

    @Test
    public void testNearestNeighbor() throws IOException, ParseException {
        try {
            ComponentId.resetGlobalCountersForTests();
            DerivedConfiguration c = assertCorrectDeriving("nearestneighbor");

            CompiledQueryProfileRegistry queryProfiles =
                    QueryProfileConfigurer.createFromConfig(new QueryProfiles(c.getQueryProfiles(), (level, message) -> {}).getConfig()).compile();
            Query q = new Query("?ranking.features.query(q_vec)=[1,2,3,4,5,6]", // length is 6, not 5
                                queryProfiles.getComponent("default"));
            fail("This should fail when q_vec is parsed as a tensor");
        } catch (QueryException e) {
            // success
            assertEquals("Invalid request parameter", e.getMessage());
        }
    }

}
