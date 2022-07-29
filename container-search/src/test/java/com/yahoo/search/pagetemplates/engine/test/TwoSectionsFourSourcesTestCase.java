// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.pagetemplates.engine.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.pagetemplates.engine.Organizer;
import com.yahoo.search.pagetemplates.engine.resolvers.DeterministicResolver;
import com.yahoo.search.pagetemplates.model.Choice;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * @author bratseth
 */
public class TwoSectionsFourSourcesTestCase extends ExecutionAbstractTestCase {

    @Test
    void testExecution() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("TwoSectionsFourSources.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source2", 4));
        result.hits().add(createHits("source3", 12));
        result.hits().add(createHits("source4", 13));

        new Organizer().organize(page, new DeterministicResolver().resolve(page, query, result), result);

        // Check execution:
        // Two subsections with two sources each, the first grouped the second blended
        assertEquals(2, result.hits().size());
        HitGroup section1 = (HitGroup) result.hits().get(0);
        HitGroup section2 = (HitGroup) result.hits().get(1);
        assertGroupedSource3Source1(section1.asList());
        assertBlendedSource4Source2(section2.asList());

        // Check rendering
        assertRendered(result, "TwoSectionsFourSourcesResult.xml");
    }

    @Test
    void testExecutionMissingOneSource() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("TwoSectionsFourSources.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source3", 12));
        result.hits().add(createHits("source4", 13));

        new Organizer().organize(page, new DeterministicResolver().resolve(page, query, result), result);

        // Check execution:
        // Two subsections with two sources each, the first grouped the second blended
        assertEquals(2, result.hits().size());
        HitGroup section1 = (HitGroup) result.hits().get(0);
        HitGroup section2 = (HitGroup) result.hits().get(1);
        assertGroupedSource3Source1(section1.asList());
        assertEqualHitGroups(createHits("source4", 10), section2);
    }

    @Test
    void testExecutionMissingTwoSources() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("TwoSectionsFourSources.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);
        result.hits().add(createHits("source1", 3));
        result.hits().add(createHits("source3", 12));

        new Organizer().organize(page, new DeterministicResolver().resolve(page, query, result), result);

        // Check execution:
        // Two subsections with two sources each, the first grouped the second blended
        assertEquals(2, result.hits().size());
        HitGroup section1 = (HitGroup) result.hits().get(0);
        HitGroup section2 = (HitGroup) result.hits().get(1);
        assertGroupedSource3Source1(section1.asList());
        assertEquals(0, section2.size());
    }

    @Test
    void testExecutionMissingAllSources() {
        // Create the page template
        Choice page = Choice.createSingleton(importPage("TwoSectionsFourSources.xml"));

        // Create a federated result
        Query query = new Query();
        Result result = new Result(query);

        new Organizer().organize(page, new DeterministicResolver().resolve(page, query, result), result);

        // Check execution:
        // Two subsections with two sources each, the first grouped the second blended
        assertEquals(2, result.hits().size());
        HitGroup section1 = (HitGroup) result.hits().get(0);
        HitGroup section2 = (HitGroup) result.hits().get(1);
        assertEquals(0, section1.size());
        assertEquals(0, section2.size());
    }

    private void assertGroupedSource3Source1(List<Hit> hits) {
        assertEquals(8,hits.size());
        assertEquals("source3-1",hits.get(0).getId().stringValue());
        assertEquals("source3-2",hits.get(1).getId().stringValue());
        assertEquals("source3-3",hits.get(2).getId().stringValue());
        assertEquals("source3-4",hits.get(3).getId().stringValue());
        assertEquals("source3-5",hits.get(4).getId().stringValue());
        assertEquals("source1-1",hits.get(5).getId().stringValue());
        assertEquals("source1-2",hits.get(6).getId().stringValue());
        assertEquals("source1-3",hits.get(7).getId().stringValue());
    }

    private void assertBlendedSource4Source2(List<Hit> hits) {
        assertEquals(10,hits.size());
        assertEquals("source4-1",hits.get(0).getId().stringValue());
        assertEquals("source2-1",hits.get(1).getId().stringValue());
        assertEquals("source4-2",hits.get(2).getId().stringValue());
        assertEquals("source2-2",hits.get(3).getId().stringValue());
        assertEquals("source4-3",hits.get(4).getId().stringValue());
        assertEquals("source2-3",hits.get(5).getId().stringValue());
        assertEquals("source4-4",hits.get(6).getId().stringValue());
        assertEquals("source2-4",hits.get(7).getId().stringValue());
        assertEquals("source4-5",hits.get(8).getId().stringValue());
        assertEquals("source4-6",hits.get(9).getId().stringValue());
    }

}
