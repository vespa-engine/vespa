// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.PageTemplate;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.Renderer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ChoiceOfRenderersTestCase extends ExecutionAbstractTestCase {

    //This test is order dependent. Fix this!!
    @Test
    void testExecution() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("ChoiceOfRenderers.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source2", 3));
        result.hits().add(createHits("source3", 3));

        // Resolve
        Resolver resolver = new DeterministicResolver();
        Resolution resolution = resolver.resolve(page, query, result);
        assertEquals(1, resolution.getResolution((Choice) ((PageTemplate) page.get(0).get(0)).getSection().elements(Renderer.class).get(0)));
        assertEquals(2, resolution.getResolution((Choice) ((PageTemplate) page.get(0).get(0)).getSection().elements(Renderer.class).get(1)));

        // Execute
        Organizer organizer = new Organizer();
        organizer.organize(page, resolution, result);

        assertEquals(6, result.getConcreteHitCount());
        assertEquals(6, result.getHitCount());

        // Check rendering
        assertRendered(result, "ChoiceOfRenderersResult.xml");
    }
}
