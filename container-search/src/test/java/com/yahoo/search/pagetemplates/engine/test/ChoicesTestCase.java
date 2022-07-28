// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.Resolver;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.pagetemplates.model.PageElement;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * @author bratseth
 */
public class ChoicesTestCase extends ExecutionAbstractTestCase {

    @Test
    void testExecution() {
        // Create the page template (second alternative will be chosen)
        List<PageElement> pages = new ArrayList<>();
        pages.add(importPage("AnySource.xml"));
        pages.add(importPage("Choices.xml"));
        Choice page = Choice.createSingletons(pages);

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("news", 3));
        result.hits().add(createHits("web", 3));
        result.hits().add(createHits("blog", 3));
        result.hits().add(createHits("images", 3));

        // Resolve
        Resolver resolver = new DeterministicResolver();
        Resolution resolution = resolver.resolve(page, query, result);

        // Execute
        Organizer organizer = new Organizer();
        organizer.organize(page, resolution, result);

        // Check rendering
        assertRendered(result, "ChoicesResult.xml");
    }

}
