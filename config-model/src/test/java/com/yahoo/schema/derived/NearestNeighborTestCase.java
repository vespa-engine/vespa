// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.schema.derived;

import com.yahoo.component.ComponentId;
import com.yahoo.search.Query;
import com.yahoo.search.query.profile.compiled.CompiledQueryProfileRegistry;
import com.yahoo.schema.parser.ParseException;
import com.yahoo.vespa.model.container.search.QueryProfiles;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

public class NearestNeighborTestCase extends AbstractExportingTestCase {

    @Test
    void testNearestNeighbor() throws IOException, ParseException {
        try {
            ComponentId.resetGlobalCountersForTests();
            DerivedConfiguration c = assertCorrectDeriving("nearestneighbor");

            CompiledQueryProfileRegistry queryProfiles = CompiledQueryProfileRegistry.fromConfig(new QueryProfiles(c.getQueryProfiles(), (level, message) -> {
            }).getConfig());
            Query q = new Query("?ranking.features.query(q_vec)=[1,2,3,4,5,6]", // length is 6, not 5
                    queryProfiles.getComponent("default"));
            fail("This should fail when q_vec is parsed as a tensor");
        } catch (IllegalArgumentException e) {
            // success
            assertEquals("Could not set 'ranking.features.query(q_vec)' to '[1,2,3,4,5,6]'", e.getMessage());
        } catch (RuntimeException e) {
            e.printStackTrace();
            throw e;
        }
    }

}
