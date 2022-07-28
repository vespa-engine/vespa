// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class AnySourceTestCase extends ExecutionAbstractTestCase {

    @Test
    void testExecution() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("AnySource.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source2", 3));
        result.hits().add(createHits("source3", 3));

        // Resolve (noop here)
        Resolver resolver = new DeterministicResolver();
        Resolution resolution = resolver.resolve(page, query, result);

        // Execute
        Organizer organizer = new Organizer();
        organizer.organize(page, resolution, result);

        // Check execution:
        // all three sources, ordered by relevance, source 3 first in each relevance group
        HitGroup hits = result.hits();
        assertEquals(9, hits.size());
        assertEquals("source3-1", hits.get(0).getId().stringValue());
        assertEquals("source1-1", hits.get(1).getId().stringValue());
        assertEquals("source2-1", hits.get(2).getId().stringValue());
        assertEquals("source3-2", hits.get(3).getId().stringValue());
        assertEquals("source1-2", hits.get(4).getId().stringValue());
        assertEquals("source2-2", hits.get(5).getId().stringValue());
        assertEquals("source3-3", hits.get(6).getId().stringValue());
        assertEquals("source1-3", hits.get(7).getId().stringValue());
        assertEquals("source2-3", hits.get(8).getId().stringValue());

        // Check rendering
        assertRendered(result, "AnySourceResult.xml");
    }

}
