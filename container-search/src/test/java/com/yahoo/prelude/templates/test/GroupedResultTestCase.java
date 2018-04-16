// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.templates.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 * Tests composition of grouped results using the HitGroup class
 *
 * @author bratseth
 */
public class GroupedResultTestCase {

    @Test
    public void testGroupedResult() {
        Result result=new Result(new Query("?query=foo"));
        HitGroup hitGroup1=new HitGroup("group1",300);
        hitGroup1.add(new Hit("group1.1",200));
        HitGroup hitGroup2=new HitGroup("group2",600);
        Hit topLevelHit1=new Hit("toplevel.1",500);
        Hit topLevelHit2=new Hit("toplevel.2",700);
        result.hits().add(hitGroup1);
        result.hits().add(topLevelHit1);
        result.hits().add(hitGroup2);
        result.hits().add(topLevelHit2);
        hitGroup1.add(new Hit("group1.2",800));
        hitGroup2.add(new Hit("group2.1",800));
        hitGroup2.add(new Hit("group2.2",300));
        hitGroup2.add(new Hit("group2.3",500));

        // Should have 7 concrete hits, ordered as
        // toplevel.2
        // group2
        //   group2.1
        //   group2.3
        //   group2.2
        // toplevel.1
        // group1
        //   group1.2
        //   group1.1
        // Assert this:

        assertEquals(7,result.getConcreteHitCount());
        assertEquals(4,result.getHitCount());

        Hit topLevel2=result.hits().get(0);
        assertEquals("toplevel.2",topLevel2.getId().stringValue());

        HitGroup returnedGroup2=(HitGroup)result.hits().get(1);
        assertEquals(3,returnedGroup2.getConcreteSize());
        assertEquals(3,returnedGroup2.size());
        assertEquals("group2.1",returnedGroup2.get(0).getId().stringValue());
        assertEquals("group2.3",returnedGroup2.get(1).getId().stringValue());
        assertEquals("group2.2",returnedGroup2.get(2).getId().stringValue());

        Hit topLevel1=result.hits().get(2);
        assertEquals("toplevel.1",topLevel1.getId().stringValue());

        HitGroup returnedGroup1=(HitGroup)result.hits().get(3);
        assertEquals(2,returnedGroup1.getConcreteSize());
        assertEquals(2,returnedGroup1.size());
        assertEquals("group1.2",returnedGroup1.get(0).getId().stringValue());
        assertEquals("group1.1",returnedGroup1.get(1).getId().stringValue());
    }

}
