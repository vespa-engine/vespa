// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @author bratseth
 */
public class FillingTestCase {

    @Test
    void testFillingAPIConsistency() {
        HitGroup group = new HitGroup();
        group.add(new Hit("hit:1"));
        group.add(new Hit("hit:2"));
        assertTrue(group.isFilled("summary"));
    }

    @Test
    void testFillingAPIConsistencyTwoPhase() {
        HitGroup group = new HitGroup();
        group.add(createNonFilled("hit:1"));
        group.add(createNonFilled("hit:2"));
        assertFalse(group.isFilled("summary"));
        fillHitsIn(group, "summary");
        group.analyze();
        assertTrue(group.isFilled("summary"));  // consistent again
    }

    @Test
    void testFillingAPIConsistencyThreePhase() {
        HitGroup group = new HitGroup();
        group.add(createNonFilled("hit:1"));
        group.add(createNonFilled("hit:2"));
        assertFalse(group.isFilled("summary"));
        assertFalse(group.isFilled("otherSummary"));
        fillHitsIn(group, "otherSummary");
        group.analyze();
        assertFalse(group.isFilled("summary"));
        assertTrue(group.isFilled("otherSummary"));
        fillHitsIn(group, "summary");
        assertTrue(group.isFilled("otherSummary"));
        group.analyze();
        assertTrue(group.isFilled("summary"));  // consistent again
        assertTrue(group.isFilled("otherSummary"));
    }

    @Test
    void testPartiallyFilledWith2Hits() {
        Hit hit1 = new Hit("id1");
        Hit hit2 = new Hit("id2");

        hit1.setFilled("summary");
        hit2.setFillable();

        HitGroup hits = new HitGroup();
        hits.add(hit1);
        hits.add(hit2);

        assertEquals(Collections.emptySet(), hits.getFilled());
    }

    @Test
    void testPartiallyFilledDiverse() {
        Hit hit1 = new Hit("id1");
        Hit hit2 = new Hit("id2");
        Hit hit3 = new Hit("id3");

        hit1.setFilled("summary1");
        hit1.setFilled("summary2");
        hit2.setFilled("summary1");
        hit3.setFilled("summary1");

        HitGroup hits = new HitGroup();
        hits.add(hit1);
        hits.add(hit2);
        hits.add(hit3);

        assertEquals(Collections.singleton("summary1"), hits.getFilled());
    }

    private Hit createNonFilled(String id) {
        Hit hit=new Hit(id);
        hit.setFillable();
        return hit;
    }

    private void fillHitsIn(HitGroup group,String summary) {
        for (Hit hit : group.asList()) {
            if (hit.isMeta()) continue;
            hit.setFilled(summary);
        }
    }

}
