// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.Resolution;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class ChoiceOfSubsectionsTestCase extends ExecutionAbstractTestCase {

    @Test
    void testExecution() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("ChoiceOfSubsections.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source2", 3));
        result.hits().add(createHits("source3", 3));
        result.hits().add(createHits("source4", 3));

        new Organizer().organize(page, new DeterministicResolverAssertingMethod().resolve(page, query, result), result);

        // Check execution:
        // Two subsections with one source each
        assertEquals(2, result.hits().size());
        HitGroup section1 = (HitGroup) result.hits().get(0);
        HitGroup section2 = (HitGroup) result.hits().get(1);
        assertEquals("section", section1.types().stream().collect(Collectors.joining(", ")));
        assertEquals("section", section2.types().stream().collect(Collectors.joining(", ")));
        assertEqualHitGroups(createHits("source2", 3), section1);
        assertEqualHitGroups(createHits("source4", 3), section2);

        // Check rendering
        assertRendered(result, "ChoiceOfSubsectionsResult.xml");
    }

    /** Same as deterministic resolver, but asserts that it received the correct method names for each choice */
    private static class DeterministicResolverAssertingMethod extends DeterministicResolver {

        private int invocationNumber = 0;

        /** Chooses the last alternative of any choice */
        @Override
        public void resolve(Choice choice, Query query, Result result, Resolution resolution) {
            invocationNumber++;
            if (invocationNumber == 2)
                assertEquals("method1", choice.getMethod());
            else if (invocationNumber == 3)
                assertEquals("method2", choice.getMethod());
            else if (invocationNumber > 3)
                throw new IllegalStateException("Unexpected number of resolver invocations");

            super.resolve(choice, query, result, resolution);
        }

    }

}
