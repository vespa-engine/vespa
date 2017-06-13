// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;

/**
 * @author bratseth
 */
public class FillingTestCase extends junit.framework.TestCase {

    public void testFillingAPIConsistency() {
        HitGroup group=new HitGroup();
        group.add(new Hit("hit:1"));
        group.add(new Hit("hit:2"));
        assertTrue(group.isFilled("summary"));
    }

    public void testFillingAPIConsistencyTwoPhase() {
        HitGroup group=new HitGroup();
        group.add(createNonFilled("hit:1"));
        group.add(createNonFilled("hit:2"));
        assertFalse(group.isFilled("summary"));
        fillHitsIn(group, "summary");
        group.analyze();
        assertTrue(group.isFilled("summary"));  // consistent again
    }

    public void testFillingAPIConsistencyThreePhase() {
        HitGroup group=new HitGroup();
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
