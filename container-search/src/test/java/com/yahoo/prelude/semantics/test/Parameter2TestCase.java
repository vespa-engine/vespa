// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package com.yahoo.prelude.semantics.test;

import com.yahoo.search.Query;

/**
 * Tests parameter matching and production
 *
 * @author bratseth
 */
public class Parameter2TestCase extends RuleBaseAbstractTestCase {

    public Parameter2TestCase(String name) {
        super(name,"parameter2.sr");
    }

    /** Tests parameter production */
    public void testParameterProduction() {
        assertRankParameterSemantics("a","a&ranking=usrank","date",0);
    }

    private void assertRankParameterSemantics(String producedQuery,String inputQuery,
                                              String rankParameterValue,int tracelevel) {
        Query query=new Query("?query=" + inputQuery + "&tracelevel=0&tracelevel.rules=" + tracelevel);
        query.properties().set("tracelevel.rules", tracelevel);
        assertSemantics(producedQuery, query);
        assertEquals(rankParameterValue,query.getRanking().getProfile());
    }

}
