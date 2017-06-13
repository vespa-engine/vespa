// Copyright 2016 Yahoo Inc. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.search.result.test;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.result.Coverage;

/**
 * @author <a href="mailto:steinar@yahoo-inc.com">Steinar Knutsen</a>
 */
public class CoverageTestCase extends junit.framework.TestCase {

    public void testZeroCoverage() {
        Coverage c = new Coverage(0L, 0, false, 0);
        assertEquals(0, c.getResultPercentage());
        assertEquals(0, c.getResultSets());
    }

    public void testActiveCoverage() {
        Coverage c = new Coverage(6, 5);
        assertEquals(5, c.getActive());
        assertEquals(6, c.getDocs());

        Coverage d = new Coverage(7, 6);
        c.merge(d);
        assertEquals(11, c.getActive());
        assertEquals(13, c.getDocs());
    }

    public void testDefaultCoverage() {
        boolean create=true;

        Result r1=new Result(new Query());
        assertEquals(0,r1.getCoverage(create).getResultSets());
        Result r2=new Result(new Query());

        r1.mergeWith(r2);
        assertEquals(0,r1.getCoverage(create).getResultSets());
    }

    public void testDefaultSearchScenario() {
        boolean create=true;

        Result federationSearcherResult=new Result(new Query());
        Result singleSourceResult=new Result(new Query());
        federationSearcherResult.mergeWith(singleSourceResult);
        assertNull(federationSearcherResult.getCoverage(!create));
        assertEquals(0,federationSearcherResult.getCoverage(create).getResultSets());
    }

    public void testRequestingCoverageSearchScenario() {
        boolean create=true;

        Result federationSearcherResult=new Result(new Query());
        Result singleSourceResult=new Result(new Query());
        singleSourceResult.setCoverage(new Coverage(10,1,true));
        federationSearcherResult.mergeWith(singleSourceResult);
        assertEquals(1,federationSearcherResult.getCoverage(create).getResultSets());
    }

}
